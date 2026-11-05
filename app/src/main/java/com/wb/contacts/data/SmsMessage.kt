package com.wb.contacts.data

import android.content.Context
import com.wb.contacts.R
import org.json.JSONObject

/**
 * 短信记录。整条序列化后加密落盘，库里没有任何明文内容。
 *
 * type 对齐系统 Telephony.Sms 的 MESSAGE_TYPE_*：
 * 1 收件箱 / 2 已发送 / 3 草稿 / 4 外发中 / 5 失败 / 6 队列中
 */
data class SmsMessage(
    val id: Long = 0L,
    val address: String = "",
    val body: String = "",
    val date: Long = 0L,
    val type: Int = 1,
    val read: Boolean = false,
    /** 线程 id，同一号码的多条短信归到一个会话 */
    val threadId: Long = 0L,
    /** 导入时的系统短信 id，用于重复导入时去重 */
    val sourceSmsId: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
) {

    fun isOutgoing(): Boolean = type == 2 || type == 4 || type == 5 || type == 6

    fun typeLabel(context: Context): String = context.getString(
        when (type) {
            1 -> R.string.sms_type_received
            2 -> R.string.sms_type_sent
            3 -> R.string.sms_type_draft
            4 -> R.string.sms_type_sending
            5 -> R.string.sms_type_failed
            6 -> R.string.sms_type_queued
            else -> R.string.sms_type_unknown
        }
    )

    fun toJson(): String = JSONObject().apply {
        put("address", address)
        put("body", body)
        put("date", date)
        put("type", type)
        put("read", read)
        put("threadId", threadId)
        put("sourceSmsId", sourceSmsId)
        put("createdAt", createdAt)
    }.toString()

    fun toPayload(cipher: SessionCipher): ByteArray = cipher.encryptText(toJson())

    companion object {
        fun fromJson(json: String, id: Long = 0L): SmsMessage {
            val o = JSONObject(json)
            return SmsMessage(
                id = id,
                address = o.optString("address"),
                body = o.optString("body"),
                date = o.optLong("date"),
                type = o.optInt("type", 1),
                read = o.optBoolean("read"),
                threadId = o.optLong("threadId"),
                sourceSmsId = o.optLong("sourceSmsId"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }

        fun fromPayload(payload: ByteArray, id: Long, cipher: SessionCipher): SmsMessage =
            fromJson(cipher.decryptText(payload), id)
    }
}
