package com.wb.contacts.system

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.Telephony
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
        val threadId: Long,
        /** 所属 SIM 卡订阅 id，双卡机上写回时必须带上，否则会落到别的分组 */
        val subId: Long
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
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.SUBSCRIPTION_ID
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
            val subIdx = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)
            while (cursor.moveToNext()) {
                result.add(
                    SysSms(
                        id = cursor.getLong(idIdx),
                        address = cursor.getString(addrIdx).orEmpty().trim(),
                        body = cursor.getString(bodyIdx).orEmpty(),
                        date = cursor.getLong(dateIdx),
                        type = cursor.getInt(typeIdx),
                        read = cursor.getInt(readIdx) == 1,
                        threadId = cursor.getLong(threadIdx),
                        subId = if (subIdx >= 0 && !cursor.isNull(subIdx)) cursor.getLong(subIdx) else -1L
                    )
                )
            }
        }
        return result
    }

    private val SMS_URI: Uri = Telephony.Sms.CONTENT_URI
    private val MMS_URI: Uri = Telephony.Mms.CONTENT_URI

    /**
     * 系统里当前还能查到的短信分项。
     *
     * 文字短信在 `content://sms`，彩信在 `content://mms`，**是两张独立的表**。
     * 早前只统计前者，清空后明明还剩几条彩信，界面却报「系统短信 0 条」——
     * 那不是删干净了，是压根没去看彩信表。
     */
    data class Inventory(
        val sms: Int,
        val mms: Int,
        /** `content://sms` 里按 sub_id 分组；本应用看不到的订阅不会出现在这里 */
        val perSub: Map<Long, Int>
    ) {
        val total: Int get() = sms + mms
    }

    fun inventory(context: Context): Inventory {
        val perSub = HashMap<Long, Int>()
        remainingWithSubId(context).forEach { (_, sub) ->
            perSub[sub] = (perSub[sub] ?: 0) + 1
        }
        return Inventory(countUri(context, SMS_URI), countUri(context, MMS_URI), perSub)
    }

    /** 界面上的「系统短信」条数 —— 文字短信 + 彩信，别再只报一张表 */
    fun countAll(context: Context): Int =
        countUri(context, SMS_URI) + countUri(context, MMS_URI)

    /** 单张表的条数；ROM 上 Provider 不可读时按 0 处理，不让整个页面崩掉 */
    private fun countUri(context: Context, uri: Uri): Int =
        runCatching {
            context.contentResolver.query(
                uri, arrayOf(Telephony.Sms._ID), null, null, null
            )?.use { it.count } ?: 0
        }.getOrDefault(0)

    /**
     * 系统里仍然存在的文字短信及其所属的 SIM 卡订阅 ID。
     * 列名做容错：部分 ROM 没有 sub_id 或 _id 列，早前的 getColumnIndexOrThrow
     * 会直接抛异常，被外层误判成「系统拒绝删除」。
     */
    fun remainingWithSubId(context: Context): List<Pair<Long, Long>> {
        val out = ArrayList<Pair<Long, Long>>()
        runCatching {
            context.contentResolver.query(
                SMS_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.SUBSCRIPTION_ID),
                null, null, null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                val subIdx = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)
                if (idIdx >= 0) {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIdx)
                        val sub = if (subIdx >= 0 && !cursor.isNull(subIdx)) cursor.getLong(subIdx) else -1L
                        out += id to sub
                    }
                }
            }
        }
        return out
    }

    /** 彩信表里残留的 id，按 id 单独删才能触发 part / addr 的级联清理 */
    private fun remainingMmsIds(context: Context): List<Long> {
        val out = ArrayList<Long>()
        runCatching {
            context.contentResolver.query(
                MMS_URI, arrayOf(Telephony.Sms._ID), null, null, null
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(Telephony.Sms._ID)
                while (idx >= 0 && cursor.moveToNext()) out += cursor.getLong(idx)
            }
        }
        return out
    }

    /**
     * 一次清空的结果。
     *
     * 文字短信和彩信分开记，剩下的也分开记 —— 用户看到「还剩 4 条」时，
     * 得能知道剩的是彩信还是文字短信，好决定要不要手动去删。
     */
    data class DeleteOutcome(
        val deleted: Int,
        val remaining: Int,
        val denied: Boolean,
        val smsDeleted: Int = deleted,
        val mmsDeleted: Int = 0,
        val smsRemaining: Int = remaining,
        val mmsRemaining: Int = 0
    )

    /**
     * 删除系统短信（文字短信 + 彩信）。
     *
     * ⚠️ Android 4.4 起只有「默认短信应用」才能删；权限不足时系统会抛
     * SecurityException 或静默返回 0，这里如实回报。
     *
     * 两张表分开处理：
     * · `content://sms` —— 文字短信
     * · `content://mms` —— 彩信。早前完全没碰这张表，用户清空后还剩几条彩信，
     *   界面却因为只查 sms 表而显示「系统短信 0 条」。
     *
     * 第二轮按 id 精确补删：SMS 残留按可见的 sub_id 分组各删一次；
     * MMS 逐条按 id 删，让 Provider 级联清掉 part / addr。
     *
     * 注意：AOSP SmsProvider 的 URI 表里**没有** `content://sms/sub_id=<id>`
     * 这种路径（只有 icc / icc_subId），早前拼的那个 URI 删除必然返回 0，
     * 属于空转，已移除。
     */
    fun deleteAll(context: Context): DeleteOutcome {
        val resolver = context.contentResolver
        var smsDeleted = 0
        var mmsDeleted = 0
        var denied = false

        // 第一轮：两张表各做一次全量删
        runCatching { resolver.delete(SMS_URI, null, null) }
            .onSuccess { smsDeleted += it }
            .onFailure { if (it is SecurityException) denied = true }
        runCatching { resolver.delete(MMS_URI, null, null) }
            .onSuccess { mmsDeleted += it }
            .onFailure { /* 个别 ROM 不给删彩信表，回读时如实反映 */ }

        // 第二轮：残留按 id 精确补删
        remainingWithSubId(context).groupBy { it.second }.forEach { (_, items) ->
            smsDeleted += deleteByIds(context, SMS_URI, items.map { it.first })
        }
        remainingMmsIds(context).forEach { id ->
            mmsDeleted += runCatching {
                resolver.delete(ContentUris.withAppendedId(MMS_URI, id), null, null)
            }.getOrDefault(0)
        }

        val inv = inventory(context)
        val deleted = smsDeleted + mmsDeleted
        val remaining = inv.total
        // 一条都没删掉、且系统确实拒绝 —— 判定为没有删除权限
        if (deleted == 0 && denied) {
            return DeleteOutcome(0, remaining, true, 0, 0, inv.sms, inv.mms)
        }
        // 一条都没删掉、但也没报错 —— 系统静默拒绝（通常是没设为默认短信应用）
        if (deleted == 0 && remaining > 0) {
            return DeleteOutcome(0, remaining, true, 0, 0, inv.sms, inv.mms)
        }
        return DeleteOutcome(
            deleted, remaining, denied,
            smsDeleted, mmsDeleted, inv.sms, inv.mms
        )
    }

    /** 按 id 精确删除（分批，避免 SQL 变量超限） */
    fun deleteByIds(context: Context, ids: List<Long>): Int =
        deleteByIds(context, SMS_URI, ids)

    private fun deleteByIds(context: Context, uri: Uri, ids: List<Long>): Int {
        if (ids.isEmpty()) return 0
        var deleted = 0
        ids.chunked(200).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            deleted += runCatching {
                context.contentResolver.delete(
                    uri,
                    "${Telephony.Sms._ID} IN ($placeholders)",
                    chunk.map { it.toString() }.toTypedArray()
                )
            }.getOrDefault(0)
        }
        return deleted
    }

    /**
     * 把本应用里的短信写回系统。
     *
     * ⚠️ 调用方必须先确认本应用是「默认短信应用」——
     * Android 4.4 起，只有默认短信应用才能往 SMS Provider 里写。
     * 不是默认短信应用时，`insert` **不报错、也返回非 null Uri，
     * 但短信根本不会出现在短信应用里**。那种情况下不能相信 insert 的返回值，
     * 必须靠 [isDefaultSmsApp] 提前拦住。
     */
    fun exportToSystem(context: Context, messages: List<SmsMessage>): ExportSummary =
        exportToSystem(context, messages, existingKeys(context), existingThreads(context))

    /**
     * 分页写回系统。
     *
     * @param existing 系统里已有的短信指纹，整轮写入共用一份
     * @param threads  系统里**现存**的「号码 → 会话 id」映射，见 [existingThreads]
     */
    fun exportToSystem(
        context: Context,
        messages: List<SmsMessage>,
        existing: MutableSet<String>,
        threads: Map<String, Long>
    ): ExportSummary {
        val resolver = context.contentResolver
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
                // 会话 id 只在系统里**确实还有**这个号码的会话时才写。
                // 早前直接沿用导入时记下的旧 id，用户清空过系统短信后那些会话
                // 已经不存在了，短信就会挂在一个"幽灵会话"上 —— 会话列表里根本不显示。
                // 查不到就干脆不写这个字段，让系统按号码自己归组。
                threads[normalizeAddress(msg.address)]?.let { put(Telephony.Sms.THREAD_ID, it) }
                // 双卡机：带上所属订阅，否则短信会落到另一张卡的分组里
                if (msg.subId > 0) put(Telephony.Sms.SUBSCRIPTION_ID, msg.subId)
            }
            val uri = runCatching {
                resolver.insert(Telephony.Sms.CONTENT_URI, values)
            }.getOrNull()

            when {
                uri == null -> failed++
                // insert 返回了 Uri 也不等于真写进去了，回读确认一次
                !verifyInserted(context, uri, msg) -> failed++
                else -> {
                    existing.add(key)
                    created++
                }
            }
        }
        return ExportSummary(created, skippedExisting, failed)
    }

    /**
     * 回读校验：确认这条短信真的落进了系统短信库。
     *
     * Provider 可能接受了请求却没真正落库（历史上就出现过
     * 「insert 返回非 null、短信应用里却找不到」的情况），
     * 所以写完必须自己查一遍，对不上就当失败，不能算成成功。
     */
    private fun verifyInserted(context: Context, uri: Uri, msg: SmsMessage): Boolean {
        val id = uri.lastPathSegment?.toLongOrNull() ?: return false
        return runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE
                ),
                "${Telephony.Sms._ID} = ?",
                arrayOf(id.toString()),
                null
            )?.use { c ->
                if (!c.moveToFirst()) return@use false
                val addr = c.getString(1).orEmpty().trim()
                val body = c.getString(2).orEmpty()
                val date = c.getLong(3)
                addr == msg.address.trim() && body == msg.body && date == msg.date
            } ?: false
        }.getOrDefault(false)
    }

    data class ExportSummary(
        val created: Int,
        val skippedExisting: Int,
        val failed: Int
    )

    /** 系统里已有的短信指纹：地址|正文|时间 */
    fun existingKeys(context: Context): MutableSet<String> {
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

    /**
     * 系统里**现存**的「号码 → 会话 id」映射。
     *
     * 只收录系统里当前还存在的短信所挂的会话 —— 已经随清空一起消失的会话
     * 不会出现在这里，写回时也就不会被误用。
     */
    fun existingThreads(context: Context): Map<String, Long> {
        val map = HashMap<String, Long>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.THREAD_ID),
            null, null, "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val addrIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val threadIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            while (cursor.moveToNext()) {
                val addr = cursor.getString(addrIdx).orEmpty().trim()
                val thread = cursor.getLong(threadIdx)
                if (addr.isEmpty() || thread <= 0L) continue
                map.putIfAbsent(normalizeAddress(addr), thread)
            }
        }
        return map
    }

    private fun keyOf(address: String, body: String, date: Long): String =
        "$address|$body|$date"

    /**
     * 号码归一化：只保留数字，并去掉国际区号前缀。
     *
     * 系统里同一个人可能存成 `+8613800138000`、`13800138000`、
     * `138 0013 8000` 三种样子，归一化后才能对上号。
     */
    fun normalizeAddress(address: String): String {
        val digits = address.filter { it.isDigit() }
        return when {
            digits.length > 11 && digits.startsWith("86") -> digits.substring(2)
            else -> digits
        }
    }
}
