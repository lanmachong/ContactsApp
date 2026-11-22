package com.wb.contacts.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File
import java.nio.ByteBuffer

/**
 * 来电号码索引的加密持久化。
 *
 * 数据库里每条联系人各自加密，冷启动要逐条走 Keystore 解密 —— 几百条就是 1～2 秒。
 * 而来电识别服务被系统拉起后，respondToCall 一放行系统就解绑，进程随时可能被回收，
 * 异步解密来不及跑完，卡片就永远弹不出来（锁屏时系统更激进，所以"解锁弹、锁屏不弹"）。
 *
 * 这里把联系人映射整体序列化成一份密文落盘：冷启动只需 **一次** Keystore 解密 +
 * 纯 CPU 解析，毫秒级完成，进程来不及被回收。密钥仍由 [Crypto] 走 Android Keystore，
 * 不落明文，安全边界与数据库完全一致。
 *
 * 文件格式（单文件）：
 *   [1 字节版本][1 字节指纹长度][指纹 ASCII][整体加密的联系人 JSON 数组]
 * 指纹只含条数 / id 之和 / 最大更新时间，不含任何个人信息，故明文存储，用于避免重复写盘。
 */
object CallerIndexStore {

    private const val FILE_NAME = "caller_index_v1.bin"
    private const val VERSION = 1
    private const val TAG = "CallerIDDiag"

    /** 读取索引；不存在 / 版本不符 / 损坏返回 null，由调用方回退到全库解密 */
    fun load(context: Context): List<Contact>? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return runCatching {
            val raw = file.readBytes()
            if (raw.size < 2 || raw[0].toInt() != VERSION) return null
            val stampLen = raw[1].toInt() and 0xFF
            val bodyOffset = 2 + stampLen
            val plain = Crypto.decryptText(raw.copyOfRange(bodyOffset, raw.size))
            val arr = JSONArray(plain)
            ArrayList<Contact>(arr.length()).apply {
                for (i in 0 until arr.length()) add(Contact.fromJson(arr.getString(i)))
            }
        }.onFailure {
            Log.w(TAG, "index store load failed: ${it.javaClass.simpleName}")
        }.getOrNull()
    }

    /** 数据指纹变化时才重写文件，避免每次 getAll 都加密写盘 */
    fun maybeSave(context: Context, contacts: List<Contact>) {
        val stamp = fingerprint(contacts)
        if (readStamp(context) == stamp) return
        save(context, contacts, stamp)
    }

    /** 供冷启动回退全库解密后主动重建索引 */
    fun save(context: Context, contacts: List<Contact>) {
        save(context, contacts, fingerprint(contacts))
    }

    private fun save(context: Context, contacts: List<Contact>, stamp: String) {
        runCatching {
            val arr = JSONArray()
            contacts.forEach { arr.put(it.toJson()) }
            val encrypted = Crypto.encryptText(arr.toString())
            val stampBytes = stamp.toByteArray(Charsets.US_ASCII)
            if (stampBytes.size > 255) return
            val buf = ByteBuffer.allocate(1 + 1 + stampBytes.size + encrypted.size)
            buf.put(VERSION.toByte())
            buf.put(stampBytes.size.toByte())
            buf.put(stampBytes)
            buf.put(encrypted)
            File(context.filesDir, FILE_NAME).writeBytes(buf.array())
            Log.i(TAG, "index store saved count=${contacts.size}")
        }.onFailure {
            Log.w(TAG, "index store save failed: ${it.javaClass.simpleName}")
        }
    }

    private fun readStamp(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return runCatching {
            val raw = file.readBytes()
            if (raw.size < 2 || raw[0].toInt() != VERSION) return null
            val len = raw[1].toInt() and 0xFF
            if (raw.size < 2 + len) return null
            String(raw, 2, len, Charsets.US_ASCII)
        }.getOrNull()
    }

    private fun fingerprint(contacts: List<Contact>): String =
        "${contacts.size}|${contacts.sumOf { it.id }}|${contacts.maxOfOrNull { it.updatedAt } ?: 0L}"
}
