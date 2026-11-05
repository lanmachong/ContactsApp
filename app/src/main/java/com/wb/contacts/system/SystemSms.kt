package com.wb.contacts.system

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.wb.contacts.R
import com.wb.contacts.data.SmsMessage

/**
 * 系统短信读写。
 * 读：READ_SMS   删：WRITE_SMS（Android 4.4+ 普通应用删短信需要成为默认短信应用，
 * 多数机型会静默失败，这里会如实回报删除条数）
 */
object SystemSms {

    /** 系统里的一条短信 */
    data class SysSms(
        val id: Long,
        val address: String,
        val body: String,
        val date: Long,
        val type: Int,
        val read: Boolean,
        val threadId: Long
    )

    /**
     * 读取系统全部短信。
     * 用 Telephony.Sms.CONTENT_URI 驱动；注意有些短信地址为空（服务商短信），
     * 全部保留，不做任何过滤。
     */
    fun readAll(context: Context): List<SysSms> {
        val result = ArrayList<SysSms>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
                Telephony.Sms.THREAD_ID
            ),
            null, null, "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addrIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val readIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
            val threadIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            while (cursor.moveToNext()) {
                result.add(
                    SysSms(
                        id = cursor.getLong(idIdx),
                        address = cursor.getString(addrIdx).orEmpty().trim(),
                        body = cursor.getString(bodyIdx).orEmpty(),
                        date = cursor.getLong(dateIdx),
                        type = cursor.getInt(typeIdx),
                        read = cursor.getInt(readIdx) == 1,
                        threadId = cursor.getLong(threadIdx)
                    )
                )
            }
        }
        return result
    }

    fun countAll(context: Context): Int =
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            null, null, null
        )?.use { it.count } ?: 0

    /**
     * 删除系统短信。
     * ⚠️ Android 4.4 起只有「默认短信应用」才能删除他人短信；
     * 权限不足时系统会抛 SecurityException 或静默返回 0，这里如实返回实际删除条数。
     */
    fun deleteAll(context: Context): Int = runCatching {
        context.contentResolver.delete(
            Telephony.Sms.CONTENT_URI,
            null,
            null
        )
    }.getOrDefault(-1)

    /** 按 id 精确删除（分批，避免 SQL 变量超限） */
    fun deleteByIds(context: Context, ids: List<Long>): Int {
        if (ids.isEmpty()) return 0
        var deleted = 0
        ids.chunked(200).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            deleted += runCatching {
                context.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms._ID} IN ($placeholders)",
                    chunk.map { it.toString() }.toTypedArray()
                )
            }.getOrDefault(0)
        }
        return deleted
    }

    /**
     * 把本应用里的短信写回系统。
     * 和通讯录一样按「线程 + 正文 + 时间」去重，避免重复导入。
     */
    fun exportToSystem(context: Context, messages: List<SmsMessage>): ExportSummary {
        val resolver = context.contentResolver
        val existing = existingKeys(context)
        var created = 0
        var skippedExisting = 0
        var failed = 0

        messages.forEach { msg ->
            val key = keyOf(msg.address, msg.body, msg.date)
            if (key in existing) {
                skippedExisting++
                return@forEach
            }
            val values = android.content.ContentValues().apply {
                put(Telephony.Sms.ADDRESS, msg.address)
                put(Telephony.Sms.BODY, msg.body)
                put(Telephony.Sms.DATE, msg.date)
                put(Telephony.Sms.TYPE, msg.type)
                put(Telephony.Sms.READ, if (msg.read) 1 else 0)
                put(Telephony.Sms.THREAD_ID, msg.threadId)
            }
            val result = runCatching {
                resolver.insert(Telephony.Sms.CONTENT_URI, values)
            }
            if (result.isSuccess && result.getOrNull() != null) {
                existing.add(key)
                created++
            } else {
                failed++
            }
        }
        return ExportSummary(created, skippedExisting, failed)
    }

    data class ExportSummary(
        val created: Int,
        val skippedExisting: Int,
        val failed: Int
    )

    /** 系统里已有的短信指纹：地址|正文|时间 */
    private fun existingKeys(context: Context): MutableSet<String> {
        val set = HashSet<String>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            null, null, null
        )?.use { cursor ->
            val addrIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                set.add(
                    keyOf(
                        cursor.getString(addrIdx).orEmpty(),
                        cursor.getString(bodyIdx).orEmpty(),
                        cursor.getLong(dateIdx)
                    )
                )
            }
        }
        return set
    }

    private fun keyOf(address: String, body: String, date: Long): String =
        "$address|$body|$date"

    /** 导出 CSV 备份（UTF-8 BOM，Excel 直接打开不乱码） */
    fun exportCsv(context: Context, uri: Uri, messages: List<SmsMessage>): Int {
        val stream = context.contentResolver.openOutputStream(uri) ?: return -1
        stream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.write(context.getString(R.string.csv_sms_header))
            writer.newLine()
            messages.forEach { m ->
                val row = listOf(
                    formatTime(m.date),
                    m.address,
                    context.getString(
                        if (m.isOutgoing()) R.string.sms_direction_out else R.string.sms_direction_in
                    ),
                    m.typeLabel(context),
                    context.getString(if (m.read) R.string.yes else R.string.no),
                    m.body
                )
                writer.write(row.joinToString(",") { escape(it) })
                writer.newLine()
            }
        }
        return messages.size
    }

    private fun formatTime(millis: Long): String {
        if (millis <= 0L) return ""
        return runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(millis))
        }.getOrDefault("")
    }

    private fun escape(value: String): String {
        val text = value.replace("\"", "\"\"")
        return if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"$text\"" else text
    }
}
