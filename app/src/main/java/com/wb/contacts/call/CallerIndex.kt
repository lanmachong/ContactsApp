package com.wb.contacts.call

import com.wb.contacts.data.Contact

/**
 * 来电号码索引（纯内存）。
 * 数据库里全是密文，无法用 SQL 查号码，所以启动时把号码解密后建索引，
 * 来电时 O(1) 命中，保证悬浮窗能在振铃瞬间弹出。
 */
object CallerIndex {

    private const val TAIL_LENGTH = 7

    @Volatile
    private var byExact: Map<String, Contact> = emptyMap()

    @Volatile
    private var byTail: Map<String, Contact> = emptyMap()

    val size: Int get() = byExact.size

    fun update(contacts: List<Contact>) {
        val exact = HashMap<String, Contact>(contacts.size * 2)
        val tail = HashMap<String, Contact>(contacts.size * 2)
        contacts.forEach { contact ->
            listOf(contact.phone, contact.phone2).forEach { raw ->
                val digits = digitsOf(raw)
                if (digits.length >= 5) {
                    exact[digits] = contact
                    if (digits.length >= TAIL_LENGTH) {
                        tail[digits.takeLast(TAIL_LENGTH)] = contact
                    }
                }
            }
        }
        byExact = exact
        byTail = tail
    }

    /**
     * 匹配策略：先全等，再按后 7 位匹配。
     * 后者覆盖国家码（+86）、长途前缀 0、以及部分运营商回传格式差异。
     */
    fun lookup(number: String?): Contact? {
        val digits = digitsOf(number ?: return null)
        if (digits.length < 5) return null
        byExact[digits]?.let { return it }
        if (digits.length >= TAIL_LENGTH) {
            byTail[digits.takeLast(TAIL_LENGTH)]?.let { return it }
        }
        return null
    }

    fun digitsOf(raw: String): String = raw.filter { it.isDigit() }
}
