package com.wb.contacts.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wb.contacts.MainActivity
import com.wb.contacts.R
import com.wb.contacts.ui.WorkState

/**
 * 备份 / 还原 / 清空期间的保活前台服务。
 *
 * 【为什么必须有它】
 * 以前耗时任务只跑在 viewModelScope 里：屏幕熄灭后 CPU 休眠，进程被降到后台，
 * ColorOS / MIUI 等省电策略还会冻结后台进程甚至回收 Activity —— 协程被取消，
 * 界面上的进度就永远停在那里。这里同时解决三件事：
 * · 前台服务把进程优先级抬到 foreground，系统不会冻结 / 顺手杀掉
 * · PARTIAL_WAKE_LOCK 让 CPU 在熄屏后继续跑（屏幕可以关，CPU 不睡）
 * · 常驻通知显示阶段与「已处理 X / Y」，锁屏也能看到任务还活着
 *
 * Android 14 起 dataSync 类型的前台服务必须声明对应权限与 type，清单里已补齐。
 * 通知权限没被授予时 FGS 仍可运行（只是通知不进抽屉），不影响保活。
 */
class WorkGuardService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        ensureChannel(this)
        val notification = buildNotification(this, WorkState.busyText, 0, 0, WorkState.busyPhase)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure {
            // 后台重建等少数场景系统不允许起前台服务，别让应用崩掉
            runCatching { stopSelf() }
        }
        wakeLock = runCatching {
            getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
                ?.also { it.acquire(WAKELOCK_TIMEOUT_MS) }
        }.getOrNull()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 任务已经结束才被拉起时立刻收工，避免常驻通知挂在那儿
        if (!WorkState.busy) runCatching { stopSelf() }
        // 系统因内存紧张回收过服务时，只要任务还在跑就重建并重新拿 CPU 锁
        return START_STICKY
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
        runCatching { stopForeground(true) }
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun publish(done: Int, total: Int, phase: String) {
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(
                NOTIFICATION_ID,
                buildNotification(this, WorkState.busyText, done, total, phase)
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "work_guard"
        private const val NOTIFICATION_ID = 2002
        private const val WAKELOCK_TAG = "ContactsApp:WorkGuard"
        private const val WAKELOCK_TIMEOUT_MS = 30L * 60L * 1000L
        private const val NOTIFY_THROTTLE_MS = 1000L

        @Volatile
        private var instance: WorkGuardService? = null
        private var lastNotifyAt = 0L

        fun start(context: Context) {
            ensureChannel(context)
            lastNotifyAt = 0L
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, WorkGuardService::class.java)
                )
            }
        }

        /** 进度更新节流到每秒一次，上万条逐条上报也不会刷爆通知栏 */
        fun update(done: Int, total: Int, phase: String) {
            val now = System.currentTimeMillis()
            if (done < total && now - lastNotifyAt < NOTIFY_THROTTLE_MS) return
            lastNotifyAt = now
            instance?.publish(done, total, phase)
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, WorkGuardService::class.java)) }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.work_notify_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            manager.createNotificationChannel(channel)
        }

        private fun buildNotification(
            context: Context,
            title: String,
            done: Int,
            total: Int,
            phase: String
        ): Notification {
            val pending = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val text = if (total > 0) {
                context.getString(R.string.work_notify_progress, done, total)
            } else {
                context.getString(R.string.work_notify_running)
            }
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_call)
                .setContentTitle(title.ifBlank { context.getString(R.string.work_notify_title) })
                .setContentText(text)
                .setSubText(phase)
                .setContentIntent(pending)
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .apply { if (total > 0) setProgress(total, done, false) }
                .build()
        }
    }
}
