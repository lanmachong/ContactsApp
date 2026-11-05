package com.wb.contacts.util

/**
 * 号码文本处理。
 *
 * 系统通讯录里的号码格式五花八门：「138 0013 8000」「+86 138-0013-8000」「13800138000」，
 * 直接按字符串比对会互相搜不到。这里统一抽成纯数字再做匹配。
 *
 * 还有一种常见脏数据：号码被填进了「姓名」栏（用户手输或导入时错位），
 * 结果是姓名一栏是号码、电话一栏是空的，导入后按号码根本搜不到人。
 */
object PhoneText {

    /** 只保留数字：+86 138-0013-8000 → 8613800138000 */
    fun digits(text: String): String = text.filter { it.isDigit() }

    /** 号码尾部，默认后 7 位，用于「换了国家码但其实是同一人」的弱匹配 */
    fun tail(text: String, n: Int = 7): String {
        val d = digits(text)
        return if (d.length >= n) d.takeLast(n) else d
    }

    /**
     * 这段文字整体是不是一个号码。
     * 要求：纯数字 7~15 位，除数字外只允许号码符号（+ - 空格 括号）。
     * 所以「13800138000」「138 0013 8000」「+86 138-0013-8000」算，而「李四」「10086客服」不算。
     */
    fun looksLikePhone(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        val d = digits(t)
        if (d.length < 7 || d.length > 15) return false
        return t.all { ch -> ch.isDigit() || ch in "+-()（） " }
    }

    /** 姓名栏里藏着号码时把它取出来，否则返回 null */
    fun fromName(text: String): String? = if (looksLikePhone(text)) text.trim() else null

    /** 两个号码是否指向同一个人（完全一致，或后 7 位一致） */
    fun sameNumber(a: String, b: String): Boolean {
        val da = digits(a)
        val db = digits(b)
        if (da.isEmpty() || db.isEmpty()) return false
        if (da == db) return true
        return da.length >= 7 && db.length >= 7 && tail(da) == tail(db)
    }
}
