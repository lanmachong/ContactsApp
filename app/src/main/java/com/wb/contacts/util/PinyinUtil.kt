package com.wb.contacts.util

import java.nio.charset.Charset

/**
 * 轻量拼音首字母工具：基于 GB2312 区位码边界表，无需第三方库。
 * 覆盖常用汉字（一级字库），用于通讯录排序分组与首字母搜索（如 "ls" 匹配 "李四"）。
 */
object PinyinUtil {

    private val BOUNDARIES = intArrayOf(
        1601, 1637, 1833, 2078, 2274, 2302, 2433, 2594, 2787,
        3106, 3212, 3472, 3635, 3722, 3730, 3858, 4027, 4086,
        4390, 4558, 4684, 4925, 5249, 5590
    )
    private const val LETTERS = "ABCDEFGHJKLMNOPQRSTWXYZ"

    private val GBK: Charset? = try {
        Charset.forName("GBK")
    } catch (e: Exception) {
        null
    }

    fun firstLetter(ch: Char): Char? {
        if (ch.code < 128) {
            return if (ch.isLetterOrDigit()) ch.uppercaseChar() else null
        }
        val charset = GBK ?: return null
        val bytes = ch.toString().toByteArray(charset)
        if (bytes.size < 2) return null
        val hi = bytes[0].toInt() and 0xFF
        val lo = bytes[1].toInt() and 0xFF
        val code = (hi - 160) * 100 + (lo - 160)
        if (code < BOUNDARIES.first() || code > BOUNDARIES.last()) return null
        for (i in LETTERS.indices.reversed()) {
            if (code >= BOUNDARIES[i]) return LETTERS[i]
        }
        return null
    }

    /** 取每个字的首字母，如 "李四" -> "LS"，"Alice" -> "ALICE" */
    fun initials(text: String): String = buildString {
        for (ch in text) {
            val letter = firstLetter(ch) ?: continue
            if (letter.isLetterOrDigit()) append(letter)
        }
    }

    /** 用于列表分组的字母，无法识别时归入 "#" */
    fun bucket(text: String): String = initials(text).firstOrNull()?.toString() ?: "#"

    /**
     * 搜索匹配：姓名 / 号码 / 分组 / 标签 / 备注 / 拼音首字母
     *
     * [extraTerms] 用来补上「库里的值和显示出来的值不一样」的情况：
     * 分组在库里存的是内部键（__sys_import__），重复标记在 v1.4.0 起是独立布尔字段，
     * 用户搜的却是本地化后的文案，这些词由调用方按当前语言传进来。
     */
    fun matches(
        contact: com.wb.contacts.data.Contact,
        rawQuery: String,
        extraTerms: List<String> = emptyList()
    ): Boolean {
        val q = rawQuery.trim()
        if (q.isEmpty()) return true
        val upper = q.uppercase()
        return contact.name.contains(q, ignoreCase = true) ||
            contact.phone.contains(q, ignoreCase = true) ||
            contact.phone2.contains(q, ignoreCase = true) ||
            contact.groupName.contains(q, ignoreCase = true) ||
            contact.tags.contains(q, ignoreCase = true) ||
            contact.note.contains(q, ignoreCase = true) ||
            extraTerms.any { it.isNotBlank() && it.contains(q, ignoreCase = true) } ||
            initials(contact.name).contains(upper) ||
            matchesNumber(contact, q)
    }

    /**
     * 号码搜索：库里存的可能是「138 0013 8000」或「+86 138-0013-8000」，
     * 用户输入的通常是连续数字，纯字符串 contains 匹配不到。统一抽成数字再比。
     */
    private fun matchesNumber(contact: com.wb.contacts.data.Contact, q: String): Boolean {
        val qd = PhoneText.digits(q)
        if (qd.length < 3) return false
        for (raw in arrayOf(contact.phone, contact.phone2)) {
            val d = PhoneText.digits(raw)
            if (d.isEmpty()) continue
            if (d.contains(qd)) return true
            // 用户连国家码一起输了：8613800138000 应能命中 13800138000
            if (qd.length > d.length && qd.length >= 7 && PhoneText.tail(qd) == PhoneText.tail(d)) return true
            // 后 7 位一致即可视为同一号码
            if (qd.length >= 7 && d.length >= 7 && PhoneText.tail(qd) == PhoneText.tail(d)) return true
        }
        return false
    }
}
