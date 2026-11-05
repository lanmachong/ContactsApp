package com.wb.contacts.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 联系人实体。整条记录序列化后整体加密写入数据库，
 * 因此库中不存在任何明文字段（姓名 / 号码 / 备注均不可读）。
 */
data class Contact(
    val id: Long = 0L,
    val name: String = "",
    val phone: String = "",
    val phone2: String = "",
    val groupName: String = "",
    val tags: String = "",
    val note: String = "",
    val favorite: Boolean = false,
    /**
     * 导入时记录的系统 raw contact id 列表，用于精确回删。
     * 一个人可能由多个 raw contact 聚合而成（本机 + 云账号 + SIM），
     * 必须全部记住，否则清理时只删掉其中一条，人还在系统里。
     */
    val sourceRawIds: List<Long> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {

    fun digits(): String = phone.filter { it.isDigit() }

    fun hasValidPhone(): Boolean = digits().isNotEmpty()

    fun toJson(): String = JSONObject().apply {
        put("name", name)
        put("phone", phone)
        put("phone2", phone2)
        put("group", groupName)
        put("tags", tags)
        put("note", note)
        put("favorite", favorite)
        put("rawIds", JSONArray(sourceRawIds))
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }.toString()

    fun toPayload(): ByteArray = Crypto.encryptText(toJson())

    companion object {

        fun fromJson(json: String, id: Long = 0L): Contact {
            val o = JSONObject(json)
            return Contact(
                id = id,
                name = o.optString("name"),
                phone = o.optString("phone"),
                phone2 = o.optString("phone2"),
                groupName = o.optString("group"),
                tags = o.optString("tags"),
                note = o.optString("note"),
                favorite = o.optBoolean("favorite"),
                sourceRawIds = readRawIds(o),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
            )
        }

        /** 兼容旧版单值 rawId 字段，避免老数据丢失「可清理」能力 */
        private fun readRawIds(o: JSONObject): List<Long> {
            o.optJSONArray("rawIds")?.let { array ->
                return (0 until array.length()).mapNotNull { i ->
                    array.optLong(i).takeIf { it > 0L }
                }
            }
            return o.optLong("rawId", 0L).takeIf { it > 0L }?.let { listOf(it) } ?: emptyList()
        }

        fun fromPayload(payload: ByteArray, id: Long): Contact =
            fromJson(Crypto.decryptText(payload), id)
    }
}
