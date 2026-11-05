package com.wb.contacts.call

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager

/**
 * 来电识别常驻服务。
 * 作用有两个：
 *  1. 前台服务保持进程存活，系统拉起时内存索引不必重建，悬浮窗能秒出；
 *  2. 监听通话状态，在电话挂断后及时撤掉悬浮窗。
 *
 * 通话状态拿不到号码时（Android 10+ 常见），号码由 CallScreeningServiceImpl 提供。
 */
class CallIdService : Service() {

    private var telephony: TelephonyManager? = null
    private var callback: TelephonyCallback? = null
    private var listener: PhoneStateListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        CallNotifier.ensureChannel(this)
        startAsForeground()
        registerCallState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshNotification()
        return START_STICKY
    }

    private fun startAsForeground() {
        runCatching {
            val notification = CallNotifier.foregroundNotification(this, CallerIndex.size)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    CallNotifier.FOREGROUND_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(CallNotifier.FOREGROUND_ID, notification)
            }
        }
    }

    private fun refreshNotification() {
        runCatching {
            val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(
                CallNotifier.FOREGROUND_ID,
                CallNotifier.foregroundNotification(this, CallerIndex.size)
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun registerCallState() {
        val manager = getSystemService(TelephonyManager::class.java) ?: return
        telephony = manager
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallState(state, null)
                    }
                }
                callback = cb
                manager.registerTelephonyCallback(mainExecutor, cb)
            } else {
                val l = object : PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallState(state, phoneNumber)
                    }
                }
                listener = l
                manager.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
            }
        }
    }

    private fun handleCallState(state: Int, number: String?) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                // 有号码才自己弹，否则交给来电筛选服务，避免弹出未知号码的空卡片
                val display = number?.takeIf { CallerIndex.digitsOf(it).length >= 5 } ?: return
                CallOverlayController.show(this, display, CallerIndex.lookup(display))
            }

            TelephonyManager.CALL_STATE_OFFHOOK,
            TelephonyManager.CALL_STATE_IDLE -> CallOverlayController.hide()
        }
    }

    override fun onDestroy() {
        CallOverlayController.hide()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                callback?.let { telephony?.unregisterTelephonyCallback(it) }
            } else {
                listener?.let { telephony?.listen(it, PhoneStateListener.LISTEN_NONE) }
            }
        }
        super.onDestroy()
    }

    companion object {
        fun start(context: android.content.Context) {
            val intent = Intent(context, CallIdService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, CallIdService::class.java))
        }
    }
}
