package com.wb.contacts.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * 「快速回复」服务。
 *
 * ## 为什么需要它
 *
 * Android 10+ 授予「默认短信应用」角色时，系统会检查清单里是否声明了
 * 四个短信组件：接收器、WAP Push 接收器、撰写 activity、回复 service。
 * 四个都齐了，角色请求才会被批准 —— 少一个，系统设置里就不会把本应用
 * 列进候选，写回短信这条路直接走不通。
 *
 * ## 它实际做什么
 *
 * 收到「用本应用回复」的请求时，把待回复的正文带进撰写界面，
 * 然后立刻结束自己 —— 这只是个转接，不做别的。
 */
class RespondViaMessageService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val body = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val reply = Intent(this, ComposeSmsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        runCatching { startActivity(reply) }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
