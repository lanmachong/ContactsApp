package com.wb.contacts.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.wb.contacts.MainActivity
import com.wb.contacts.R

/** 通知渠道与「来电识别运行中」常驻通知 */
object CallNotifier {

    const val CHANNEL_ID = "call_id"
    const val FOREGROUND_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "来电识别",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "来电时显示联系人的悬浮窗提示"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun foregroundNotification(context: Context, matched: Int): Notification {
        val content = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            content,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle("来电识别已开启")
            .setContentText("来电时会显示姓名悬浮窗 · 已识别 $matched 个号码")
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
