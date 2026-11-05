package com.wb.contacts.system

import android.content.Context
import android.provider.CallLog
import com.wb.contacts.data.CallRecord

/**
 * 系统通话记录读写。
 * 读：READ_CALL_LOG   删：WRITE_CALL_LOG
 *
 * ⚠️ WRITE_CALL_LOG 是 signature 级权限，普通应用申请不到，
 * 实际清空系统通话记录只能靠「本应用内删除 + 系统设置里清理」两条路。
 * 这里如实反映系统给出的结果，不假装成功。
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
     * 普通应用没有 WRITE_CALL_LOG，系统会抛 SecurityException 或返回 0；
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
