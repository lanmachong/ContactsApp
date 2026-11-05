package com.wb.contacts.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM 信封加密。
 * 密钥由 Android Keystore 生成并保管（不落盘、不可导出，支持设备时由 TEE/StrongBox 保护）。
 * 密文格式：[1 字节版本][12 字节 IV][密文 + 16 字节 GCM Tag]
 */
object Crypto {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "contacts_master_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128
    private const val VERSION: Byte = 1

    @Volatile
    private var cachedKey: SecretKey? = null

    private fun secretKey(): SecretKey {
        cachedKey?.let { return it }
        synchronized(this) {
            cachedKey?.let { return it }
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            val key = existing?.secretKey ?: generateKey()
            cachedKey = key
            return key
        }
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val body = cipher.doFinal(plain)
        val output = ByteArray(1 + iv.size + body.size)
        output[0] = VERSION
        System.arraycopy(iv, 0, output, 1, iv.size)
        System.arraycopy(body, 0, output, 1 + iv.size, body.size)
        return output
    }

    fun decrypt(data: ByteArray): ByteArray {
        require(data.size > IV_LENGTH + 1) { "Invalid ciphertext length" }
        require(data[0] == VERSION) { "Unsupported ciphertext version" }
        val iv = data.copyOfRange(1, 1 + IV_LENGTH)
        val body = data.copyOfRange(1 + IV_LENGTH, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(body)
    }

    fun encryptText(text: String): ByteArray = encrypt(text.toByteArray(Charsets.UTF_8))

    fun decryptText(data: ByteArray): String = String(decrypt(data), Charsets.UTF_8)

    /**
     * 提前把密钥准备好。
     * 首次访问 Keystore 要加载 provider、生成密钥，是百毫秒级的开销；
     * 冷启动时提前触发，就能和界面初始化并行，不占用首屏等待时间。
     */
    fun warmUp() {
        secretKey()
    }
}
