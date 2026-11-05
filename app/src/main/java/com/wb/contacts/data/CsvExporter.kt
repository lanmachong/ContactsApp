package com.wb.contacts.data

import android.content.Context
import android.net.Uri
import com.wb.contacts.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 加密存档的 CSV 导出。
 *
 * 【为什么单独做】
 * 短信和通话记录动辄上万条，一次性全解密进内存既慢又容易 OOM，
 * 所以这里逐页解密、逐页写出，写完一页释放一页。
 *
 * 【曾经踩过的坑】
 * 早期版本直接把「界面上当前加载的那一页」（200 条）导出，
 * 界面显示已备份一万多条，用户导出 CSV 后文件里却只有 200 条 ——
 * 备份名存实亡。现在改成直接遍历加密表的全部内容，
 * 与界面渲染到第几条完全无关。
 */
object CsvExporter {

    private const val PAGE_SIZE = 500

    /** 导出短信 CSV（UTF-8 BOM，Excel 直接打开不乱码）；返回写出的条数，-1 表示失败 */
    suspend fun exportSms(context: Context, uri: Uri, table: SecureTable<SmsMessage>): Int {
        val stream = context.contentResolver.openOutputStream(uri) ?: return -1
        var written = 0
        stream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.write(context.getString(R.string.csv_sms_header))
            writer.newLine()
            table.forEachPage(PAGE_SIZE) { chunk ->
                chunk.forEach { m ->
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
                written += chunk.size
            }
        }
        return written
    }

    /** 导出通话记录 CSV；返回写出的条数，-1 表示失败 */
    suspend fun exportCalls(context: Context, uri: Uri, table: SecureTable<CallRecord>): Int {
        val stream = context.contentResolver.openOutputStream(uri) ?: return -1
        var written = 0
        stream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.write(context.getString(R.string.csv_calls_header))
            writer.newLine()
            table.forEachPage(PAGE_SIZE) { chunk ->
                chunk.forEach { r ->
                    val row = listOf(
                        formatTime(r.date),
                        r.number,
                        r.contactName,
                        r.typeLabel(context),
                        r.duration.toString()
                    )
                    writer.write(row.joinToString(",") { escape(it) })
                    writer.newLine()
                }
                written += chunk.size
            }
        }
        return written
    }

    private fun formatTime(millis: Long): String {
        if (millis <= 0L) return ""
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
        }.getOrDefault("")
    }

    private fun escape(value: String): String {
        val text = value.replace("\"", "\"\"")
        return if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"$text\"" else text
    }
}
