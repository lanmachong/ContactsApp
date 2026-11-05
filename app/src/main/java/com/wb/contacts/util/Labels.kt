package com.wb.contacts.util

import android.content.Context
import com.wb.contacts.R

/**
 * 标签与分组的本地化桥接。
 *
 * ## 为什么要这么绕
 *
 * groupName / tags 会被写进加密数据库。如果直接存中文「系统导入」或「重复」，
 * 那用户把系统语言切成英文之后，库里就是两套值混在一起，筛选栏会出现两个几乎
 * 一样的组，重复标记也会失效。
 *
 * 所以数据库里统一存**语言无关的内部键**（__sys_import__）或独立布尔字段，
 * 真正显示的时候再通过资源翻译成当前语言的文案。
 *
 * ## 旧数据兼容
 *
 * v1.3.0 及之前存的是中文「系统导入」「重复」。
 * [normalizeGroup] / [stripLegacyDuplicateTag] 会把历史值也识别出来，因此老用户
 * 升级后无需任何数据迁移，分组筛选与重复检测照常工作。
 */
object Labels {

    /** 系统导入分组的内部键（写进数据库，不随语言变化） */
    const val GROUP_SYSTEM_IMPORT = "__sys_import__"

    /** 历史版本写入的中文分组值 */
    private const val LEGACY_GROUP_SYSTEM_IMPORT = "系统导入"

    /**
     * 历史版本把「重复」混在 tags 字符串里。
     * v1.4.0 起重复标记改用独立的布尔字段，这里只用于迁移老数据。
     */
    const val LEGACY_TAG_DUPLICATE = "重复"

    /** 标签分隔符（沿用历史版本的全角逗号） */
    const val TAG_SEPARATOR = "，"

    /** 分组值规范化：内部键与历史中文值都归一到内部键，用户自建分组原样保留 */
    fun normalizeGroup(raw: String): String = when (raw.trim()) {
        GROUP_SYSTEM_IMPORT, LEGACY_GROUP_SYSTEM_IMPORT -> GROUP_SYSTEM_IMPORT
        else -> raw
    }

    /** 分组内部键 → 当前语言的显示文案 */
    fun groupDisplay(context: Context, raw: String): String = when (raw.trim()) {
        GROUP_SYSTEM_IMPORT -> context.getString(R.string.group_system_import)
        else -> raw
    }

    /**
     * 显示文案 → 存储值。
     *
     * 用户在编辑页看到的是本地化文案，保存时要转回内部键。
     * 这里不会出现「用户手打的分组被误转」的问题：转换后的分组
     * 显示出来和用户输入的一字不差。
     */
    fun groupToStorage(context: Context, displayed: String): String {
        val text = displayed.trim()
        return if (text == context.getString(R.string.group_system_import)) {
            GROUP_SYSTEM_IMPORT
        } else {
            text
        }
    }

    /**
     * 拆分标签。
     *
     * 历史数据里的分隔符可能是全角逗号、半角逗号或分号，
     * 这里统一按四种都切，避免「重复」标签藏在另一种分隔符后面漏掉。
     */
    fun splitTags(tags: String): List<String> =
        tags.split('，', ',', '；', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** 去掉历史遗留的「重复」标签，其余标签按标准分隔符重新拼接 */
    fun stripLegacyDuplicateTag(tags: String): String =
        splitTags(tags)
            .filter { it != LEGACY_TAG_DUPLICATE }
            .joinToString(TAG_SEPARATOR)
}
