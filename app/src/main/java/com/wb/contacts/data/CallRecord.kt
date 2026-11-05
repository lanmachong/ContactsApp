package com.wb.contacts.data

import org.json.JSONObject

/**
 * 通话记录。整条序列化后加密落盘。
 *
 * type 对齐系统 CallLog.Calls 的 CALL_TYPE_*：
 * 1 来电 / 2 去电 / 3 未接 / 4 拒接 / 5 屏蔽 / 6 已接
 */
data class CallRecord(
    val id: Long = 0L,
    val number: String = "",
    val contactName: String = "",
    val type: Int = 2,
    val date: Long = 0L,
    /** 通话时长（秒） */
    val duration: Long = 0L,
    /** 导入时的系统记录 id，用于重复导入时去重 */
    val sourceCallId: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
) {

    fun isIncoming(): Boolean = type == 1 || type == 6

    fun isMissed(): Boolean = type == 3

    fun typeLabel(): String = when (type) {
        1 -> "来电"
        2 -> "去电"
        3 -> "未接"
        4 -> "拒接"
        5 -> "屏蔽"
        6 -> "已接"
        else -> "未知"
    }

    fun durationLabel(): String {
        if (duration <= 0L) return ""
        val min = duration / 60
        val sec = duration % 60
        return if (min > 0) "${min}分${sec}秒" else "${sec}秒"
    }

    fun toJson(): String = JSONObject().apply {
        put("number", number)
        put("contactName", contactName)
        put("type", type)
        put("date", date)
        put("duration", duration)
        put("sourceCallId", sourceCallId)
        put("createdAt", createdAt)
    }.toString()

    fun toPayload(cipher: SessionCipher): ByteArray = cipher.encryptText(toJson())

    companion object {
        fun fromJson(json: String, id: Long = 0L): CallRecord {
            val o = JSONObject(json)
            return CallRecord(
                id = id,
                number = o.optString("number"),
                contactName = o.optString("contactName"),
                type = o.optInt("type", 2),
                date = o.optLong("date"),
                duration = o.optLong("duration"),
                sourceCallId = o.optLong("sourceCallId"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }

        fun fromPayload(payload: ByteArray, id: Long, cipher: SessionCipher): CallRecord =
            fromJson(cipher.decryptText(payload), id)
    }
}
