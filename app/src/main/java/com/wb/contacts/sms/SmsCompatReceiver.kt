package com.wb.contacts.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.telephony.SmsMessage as AndroidSmsMessage

/**
 * 接收短信的广播接收器。
 *
 * 本应用临时充当「默认短信应用」期间，收到的文字短信必须写回系统短信库，
 * 否则用户会丢短信。同时它也是系统校验默认短信应用资格时要求的组件之一。
 *
 * ⚠️ 写回时**必须带上 sub_id**：SMS_DELIVER 广播会通过 "subscription" extra
 * 带来接收短信的订阅 id。早前漏写这个字段，新短信的 sub_id 变成 -1；
 * 而 AOSP SmsProvider 删除 content://sms 时会强制追加
 * `sub_id IN (当前活跃订阅)` 过滤，这些 -1 的记录就永远清不掉，
 * 界面计数也容易把它们显示成 0 条。
 */
class SmsCompatReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = runCatching {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        }.getOrNull().orEmpty()
        val subId = resolveSubId(intent)
        messages.forEach { store(context, it, subId) }
    }

    /** 依次尝试 long / int 形式的 subscription extra，最后退回默认短信订阅 */
    private fun resolveSubId(intent: Intent): Long {
        runCatching { intent.getLongExtra(EXTRA_SUBSCRIPTION, -1L) }
            .getOrDefault(-1L).let { if (it >= 0) return it }
        runCatching { intent.getIntExtra(EXTRA_SUBSCRIPTION, -1).toLong() }
            .getOrDefault(-1L).let { if (it >= 0) return it }
        return runCatching {
            SubscriptionManager.getDefaultSmsSubscriptionId().toLong()
        }.getOrDefault(-1L)
    }

    /** 把一条收到的短信写进系统短信库 */
    private fun store(context: Context, message: AndroidSmsMessage, subId: Long) {
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
            if (subId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
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

    private companion object {
        /** Telephony.Sms.Intents 文档里 SMS_DELIVER 的订阅 id extra 名 */
        const val EXTRA_SUBSCRIPTION = "subscription"
    }
}
