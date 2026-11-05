package com.wb.contacts.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage as AndroidSmsMessage

/**
 * 接收短信的广播接收器。
 *
 * ## 为什么需要它
 *
 * Android 4.4 起，往系统短信库写短信**只有默认短信应用能做**。
 * 而要拿到这个身份，清单里必须声明完整的短信组件，其中就包括这个接收器
 * —— 缺了它，系统设置里的「默认短信应用」候选列表里根本不会出现本应用，
 * 写回短信这条路就是死的。
 *
 * ## 它实际做什么
 *
 * 既然本应用可能短暂地成为默认短信应用，那它收到的短信就不能丢：
 * 收到 SMS_DELIVER 广播后，把短信内容**写回系统短信库**，
 * 这样即使本应用没有完整的短信界面，用户的短信也不会凭空消失。
 */
class SmsCompatReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = runCatching {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        }.getOrNull().orEmpty()
        messages.forEach { store(context, it) }
    }

    /** 把一条收到的短信写进系统短信库 */
    private fun store(context: Context, message: AndroidSmsMessage) {
        val address = message.displayOriginatingAddress?.trim().orEmpty()
        val body = message.displayMessageBody.orEmpty()
        if (address.isEmpty()) return

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, message.timestampMillis)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            // 挂到该号码对应的会话上；拿不到就不写这个字段，交给系统自己归组
            runCatching {
                val threadId = Telephony.Threads.getOrCreateThreadId(context, address)
                if (threadId > 0L) put(Telephony.Sms.THREAD_ID, threadId)
            }
        }
        runCatching {
            context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
        }
    }
}
