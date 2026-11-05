package com.wb.contacts.system

import android.content.Context
import android.provider.CallLog
import com.wb.contacts.data.CallRecord

/**
 * 系统通话记录读写。
 * 读：READ_CALL_LOG   删：WRITE_CALL_LOG
 *
 * WRITE_CALL_LOG 是 CALL_LOG 权限组里的 dangerous 运行时权限，
 * 声明后按需申请就能拿到，第三方应用可以删除系统通话记录。
 * 但个别 ROM 会拒绝授予或拒绝删除，所以这里如实反映系统给出的结果，不假装成功；
 * 调用方还要回读一次条数，确认真的删掉了。
 *
 * 写回（insert）则不同：CallLog Provider 对新增记录有额外限制，
 * 本应用实测会被系统拒绝，所以「还原数据」页不提供通话记录这一项。
 */
object SystemCallLog {

    data class SysCall(
        val id: Long,
        val number: String,
        val cachedName: String,
        val type: Int,
        val date: Long,
        val duration: Long
    )

    fun readAll(context: Context): List<SysCall> {
        val result = ArrayList<SysCall>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            ),
            null, null, "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
            val numIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val nameIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            while (cursor.moveToNext()) {
                result.add(
                    SysCall(
                        id = cursor.getLong(idIdx),
                        number = cursor.getString(numIdx).orEmpty().trim(),
                        cachedName = cursor.getString(nameIdx).orEmpty().trim(),
                        type = cursor.getInt(typeIdx),
                        date = cursor.getLong(dateIdx),
                        duration = cursor.getLong(durIdx)
                    )
                )
            }
        }
        return result
    }

    fun countAll(context: Context): Int =
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls._ID),
            null, null, null
        )?.use { it.count } ?: 0

    /**
     * 删除系统通话记录。
     * 没拿到 WRITE_CALL_LOG 时系统会抛 SecurityException 或返回 0；
     * 返回 -1 表示被系统拒绝，0 表示没删掉任何一条。
     */
    fun deleteAll(context: Context): Int = runCatching {
        context.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
    }.getOrDefault(-1)

    fun deleteByIds(context: Context, ids: List<Long>): Int {
        if (ids.isEmpty()) return 0
        var deleted = 0
        ids.chunked(200).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            deleted += runCatching {
                context.contentResolver.delete(
                    CallLog.Calls.CONTENT_URI,
                    "${CallLog.Calls._ID} IN ($placeholders)",
                    chunk.map { it.toString() }.toTypedArray()
                )
            }.getOrDefault(0)
        }
        return deleted
    }
}
