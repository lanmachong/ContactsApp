package com.wb.contacts.system

import android.content.ContentProviderOperation
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import android.telephony.PhoneNumberUtils
import com.wb.contacts.data.Contact
import com.wb.contacts.util.PhoneText

/**
 * 系统通讯录读写。
 * 读：READ_CONTACTS   写/删：WRITE_CONTACTS
 *
 * ⚠️ 删除 RawContacts 时，若联系人归属云账户（Google / 厂商账号），
 * 同步适配器会把删除同步到云端。UI 层必须在执行前明确提示用户。
 */
object SystemContacts {

    /**
     * 一条「系统里的人是这么显示的」记录。
     * 以 Contacts 表为准（用户看到几个人就是几个人），
     * rawIds 收集该人名下所有 raw contact，删除时一次性删干净。
     */
    data class SysContact(
        val contactId: Long,
        val name: String,
        val phones: List<String>,
        val rawIds: List<Long>
    ) {
        fun hasPhone(): Boolean = phones.any { number -> number.any { it.isDigit() } }
    }

    /**
     * 枚举系统通讯录的**全部**联系人。
     * 关键点：不能用 Phone 表做驱动，否则「只有名字/邮箱、没有号码」的人会被整条漏掉，
     * 表现就是导入条数比系统里少、清理之后还剩下这些人。
     */
    fun readAll(context: Context): List<SysContact> {
        val phonesByContact = HashMap<Long, MutableList<String>>()

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val contactIdx =
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val numberIdx =
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(contactIdx)
                val number = cursor.getString(numberIdx)?.trim().orEmpty()
                if (number.isEmpty()) continue
                val list = phonesByContact.getOrPut(contactId) { mutableListOf() }
                if (list.none { PhoneNumberUtils.compare(it, number) }) list.add(number)
            }
        }

        val rawIdsByContact = HashMap<Long, MutableList<Long>>()
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.CONTACT_ID
            ),
            "${ContactsContract.RawContacts.DELETED} = 0",
            null,
            null
        )?.use { cursor ->
            val rawIdx = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts._ID)
            val contactIdx = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts.CONTACT_ID)
            while (cursor.moveToNext()) {
                if (cursor.isNull(contactIdx)) continue
                val contactId = cursor.getLong(contactIdx)
                val rawId = cursor.getLong(rawIdx)
                rawIdsByContact.getOrPut(contactId) { mutableListOf() }.add(rawId)
            }
        }

        val result = ArrayList<SysContact>()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameIdx =
                cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(idIdx)
                result.add(
                    SysContact(
                        contactId = contactId,
                        name = cursor.getString(nameIdx).orEmpty(),
                        phones = phonesByContact[contactId].orEmpty(),
                        rawIds = rawIdsByContact[contactId].orEmpty()
                    )
                )
            }
        }
        return result
    }

    /** 统计系统通讯录人数（含无号码的） */
    fun countAll(context: Context): Int =
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID),
            null, null, null
        )?.use { it.count } ?: 0

    /** 当前系统里所有 raw contact id（DELETED=0），用于「清空全部」 */
    fun allRawIds(context: Context): List<Long> {
        val ids = ArrayList<Long>()
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.DELETED} = 0",
            null,
            null
        )?.use { cursor ->
            val idx = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts._ID)
            while (cursor.moveToNext()) ids.add(cursor.getLong(idx))
        }
        return ids
    }

    /**
     * 按 raw contact id 精确删除系统联系人（不误删用户后加的联系人）。
     * 分批执行，避免 SQL 变量数超限。
     */
    fun deleteByRawIds(context: Context, rawIds: List<Long>): Int {
        if (rawIds.isEmpty()) return 0
        var deleted = 0
        rawIds.chunked(150).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val selection = "${ContactsContract.RawContacts._ID} IN ($placeholders)"
            val args = chunk.map { it.toString() }.toTypedArray()
            deleted += context.contentResolver.delete(
                ContactsContract.RawContacts.CONTENT_URI,
                selection,
                args
            )
        }
        return deleted
    }

    /** 删除系统通讯录里的全部联系人 */
    fun deleteAll(context: Context): Int = deleteByRawIds(context, allRawIds(context))

    /** 导出结果 */
    data class ExportSummary(
        val created: Int,
        val skippedExisting: Int,
        val skippedNoPhone: Int,
        val failed: Int
    ) {
        val total: Int get() = created + skippedExisting + skippedNoPhone + failed
    }

    /**
     * 系统通讯录里已经存在的号码（归一化后的纯数字）。
     * 导出时用它去重，避免同一个号码被写两遍、联系人卡片翻倍。
     */
    fun existingPhoneNumbers(context: Context): MutableSet<String> {
        val set = HashSet<String>()
        context.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.NUMBER),
            null, null, null
        )?.use { cursor ->
            val idx = cursor.getColumnIndexOrThrow(Phone.NUMBER)
            while (cursor.moveToNext()) {
                val digits = PhoneText.digits(cursor.getString(idx).orEmpty())
                if (digits.isNotEmpty()) set.add(digits)
            }
        }
        return set
    }

    /**
     * 把本应用里的联系人写回系统通讯录。
     *
     * 用户装这个 App 的目的就是「把通讯录搬走」，但有人反悔、或者要卸载，
     * 得能把数据还原回系统，所以这里提供反向通路。
     *
     * 按号码去重：系统里已经有这个号码的联系人就跳过，不会写出重复卡片。
     */
    fun exportToSystem(context: Context, contacts: List<Contact>): ExportSummary {
        val resolver = context.contentResolver
        val existing = existingPhoneNumbers(context)
        var created = 0
        var skippedExisting = 0
        var skippedNoPhone = 0
        var failed = 0

        contacts.forEach { contact ->
            val phones = listOf(contact.phone, contact.phone2)
                .map { it.trim() }
                .filter { PhoneText.digits(it).isNotEmpty() }
            if (phones.isEmpty()) {
                skippedNoPhone++
                return@forEach
            }
            val normalized = phones.map { PhoneText.digits(it) }
            if (normalized.any { existing.contains(it) }) {
                skippedExisting++
                return@forEach
            }

            val ops = ArrayList<ContentProviderOperation>()
            ops.add(
                ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                    .withValue(RawContacts.ACCOUNT_TYPE, null)
                    .withValue(RawContacts.ACCOUNT_NAME, null)
                    .build()
            )
            ops.add(
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(StructuredName.DISPLAY_NAME, contact.name.ifBlank { "未命名" })
                    .build()
            )
            phones.forEachIndexed { index, number ->
                ops.add(
                    ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                        .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                        .withValue(Phone.NUMBER, number)
                        .withValue(
                            Phone.TYPE,
                            if (index == 0) Phone.TYPE_MOBILE else Phone.TYPE_OTHER
                        )
                        .build()
                )
            }

            try {
                resolver.applyBatch(ContactsContract.AUTHORITY, ops)
                normalized.forEach { existing.add(it) }
                created++
            } catch (e: Exception) {
                failed++
            }
        }
        return ExportSummary(created, skippedExisting, skippedNoPhone, failed)
    }

    /** 导出 CSV 备份（UTF-8 BOM，Excel 直接打开不乱码） */
    fun exportCsv(context: Context, uri: Uri, contacts: List<Contact>): Int {
        val stream = context.contentResolver.openOutputStream(uri) ?: return -1
        stream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.write("姓名,手机号,备用号码,分组,标签,备注,收藏")
            writer.newLine()
            contacts.forEach { c ->
                val row = listOf(
                    c.name, c.phone, c.phone2, c.groupName, c.tags, c.note,
                    if (c.favorite) "是" else "否"
                )
                writer.write(row.joinToString(",") { escape(it) })
                writer.newLine()
            }
        }
        return contacts.size
    }

    private fun escape(value: String): String {
        val text = value.replace("\"", "\"\"")
        return if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"$text\"" else text
    }
}
