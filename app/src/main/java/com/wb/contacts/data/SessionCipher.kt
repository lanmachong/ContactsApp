package com.wb.contacts.data

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 会话密钥加密。
 *
 * 为什么不用 Keystore 密钥直接加密每一条记录：
 * Keystore 的密钥在 TEE / StrongBox 里，每条记录一次加解密就是一次硬件握手，
 * 实测几十微秒到毫秒级。通讯录几百条还能忍，短信和通话记录动辄几千上万条，
 * 全量导入 / 首次加载会直接卡住十几秒。
 *
 * 做法：随机生成一把 32 字节软件密钥，用 Keystore 里的主密钥包裹后存进 SharedPreferences。
 * 主密钥永远不出安全区（不可导出），包裹后的密文落盘也不可用；
 * 解开后软件密钥只留在内存里，用软件 AES-GCM 处理每条记录 —— 快两个数量级。
 *
 * 安全强度与主密钥路径一致（AES-256-GCM），差别只是「派生层级」：
 *   主密钥（Keystore，不可导出）→ 包裹会话密钥（落盘密文）→ 记录密文
 * 根信任仍在 Keystore：主密钥没了，落盘的会话密钥也解不开。
 */
class SessionCipher(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var sessionKey: SecretKeySpec? = null

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
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
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(body)
    }

    fun encryptText(text: String): ByteArray = encrypt(text.toByteArray(Charsets.UTF_8))

    fun decryptText(data: ByteArray): String = String(decrypt(data), Charsets.UTF_8)

    /** 提前准备好密钥，避免首个界面等 Keystore */
    fun warmUp() {
        runCatching { key() }
    }

    private fun key(): SecretKeySpec {
        sessionKey?.let { return it }
        synchronized(this) {
            sessionKey?.let { return it }
            val key = loadOrCreateKey()
            sessionKey = key
            return key
        }
    }

    private fun loadOrCreateKey(): SecretKeySpec {
        val stored = prefs.getString(KEY_WRAPPED, null)
        if (stored != null) {
            // 主密钥失效时这里会抛异常，交给上层提示「数据无法解密」
            val raw = Crypto.decrypt(Base64.decode(stored, Base64.NO_WRAP))
            return SecretKeySpec(raw, "AES")
        }
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrapped = Crypto.encrypt(raw)
        prefs.edit()
            .putString(KEY_WRAPPED, Base64.encodeToString(wrapped, Base64.NO_WRAP))
            .apply()
        return SecretKeySpec(raw, "AES")
    }

    private companion object {
        const val PREFS_NAME = "secure_session"
        const val KEY_WRAPPED = "wrapped_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
        const val VERSION: Byte = 1
    }
}
