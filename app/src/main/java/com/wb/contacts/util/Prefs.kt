package com.wb.contacts.util

import android.content.Context

/** 极少量开关状态，只存「来电识别是否开启」，不含任何联系人信息 */
object Prefs {

    private const val FILE = "settings"
    private const val KEY_CALL_ID = "call_id_enabled"

    fun isCallIdEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_CALL_ID, false)

    fun setCallIdEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_CALL_ID, enabled).apply()
    }
}
