package com.wb.contacts.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 彩信推送接收器。
 *
 * 本应用不处理彩信（彩信存在 `content://mms`，不在本应用的备份范围内），
 * 但 Android 10+ 授予「默认短信应用」角色时要求清单里声明这个接收器，
 * 缺了它就拿不到身份，写回短信也就无从谈起。
 *
 * 所以这里只声明、只接收，什么都不做。
 */
class WapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
