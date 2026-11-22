package com.wb.contacts.data

import com.wb.contacts.R

/**
 * 来电识别用的动物头像目录。
 *
 * 共 10 款，顺序固定（与 strings.xml 的 avatar_names 数组一一对应）。
 * 用户没手动选过（avatarId < 0）时，按号码 / 姓名稳定映射一款：
 * 同一联系人每次都分到同一只动物，不会闪变；不同联系人大多不同，便于一眼区分。
 */
object AvatarCatalog {

    const val COUNT = 10

    /** 顺序：猫、狗、兔、熊猫、狐狸、熊、企鹅、青蛙、老虎、狮子 */
    private val RES = intArrayOf(
        R.drawable.ic_avatar_cat,
        R.drawable.ic_avatar_dog,
        R.drawable.ic_avatar_rabbit,
        R.drawable.ic_avatar_panda,
        R.drawable.ic_avatar_fox,
        R.drawable.ic_avatar_bear,
        R.drawable.ic_avatar_penguin,
        R.drawable.ic_avatar_frog,
        R.drawable.ic_avatar_tiger,
        R.drawable.ic_avatar_lion
    )

    private val NAMES = intArrayOf(
        R.string.avatar_name_cat,
        R.string.avatar_name_dog,
        R.string.avatar_name_rabbit,
        R.string.avatar_name_panda,
        R.string.avatar_name_fox,
        R.string.avatar_name_bear,
        R.string.avatar_name_penguin,
        R.string.avatar_name_frog,
        R.string.avatar_name_tiger,
        R.string.avatar_name_lion
    )

    fun drawableRes(index: Int): Int = RES[index.coerceIn(0, COUNT - 1)]

    fun nameRes(index: Int): Int = NAMES[index.coerceIn(0, COUNT - 1)]

    /**
     * 实际显示的头像序号。用户选过就用用户选的；否则按号码 / 名字稳定 hash。
     * String.hashCode() 在同一台设备上跨进程稳定，因此冷启动后仍是同一只动物。
     */
    fun resolve(contact: Contact): Int {
        if (contact.avatarId in 0 until COUNT) return contact.avatarId
        val seed = contact.digits().ifEmpty { contact.name }.ifEmpty { contact.id.toString() }
        return (seed.hashCode() and Int.MAX_VALUE) % COUNT
    }
}
