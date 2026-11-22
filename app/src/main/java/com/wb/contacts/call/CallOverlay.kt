package com.wb.contacts.call

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.wb.contacts.data.Contact

/**
 * 来电悬浮窗（解锁态）。
 * TYPE_APPLICATION_OVERLAY + 「显示在其他应用上层」权限。
 * 纯展示：只有头像 + 姓名，无按钮；接通/挂断后自动关闭。
 */
class CallOverlay(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var rootView: View? = null
    private var hideRunnable: Runnable? = null
    private var telephony: TelephonyManager? = null

    /** 实际承载卡片的 WindowManager：无障碍浮层与普通悬浮窗用的不是同一个 */
    private var hostWindowManager: WindowManager? = null

    fun canDraw(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true

    fun show(contact: Contact?, number: String) {
        hide()

        // 优先用无障碍浮层：只有这一层能盖住 realme 的全屏来电界面。
        // 无障碍服务未开启时退回普通悬浮窗（解锁态体验一致，锁屏下会被遮挡）。
        val acc = CallAccessibilityService.instance
        val wm: WindowManager
        val windowType: Int
        if (acc != null) {
            wm = acc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            if (!canDraw()) return
            wm = windowManager
            windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }

        // 卡片本身就是 CallCardView：头像 + 姓名，无查看/关闭按钮
        val view = CallCardView.build(context, contact, number, animateAvatar = false)
        val displayWidth = context.resources.displayMetrics.widthPixels
        val cardWidth = minOf(displayWidth - dp(32), dp(300))
        val params = WindowManager.LayoutParams(
            cardWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(56)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val added = runCatching { wm.addView(view, params) }
            .onFailure { android.util.Log.w("CallerIDDiag", "overlay addView failed: ${it.javaClass.simpleName} ${it.message}") }
            .isSuccess
        if (!added) return
        rootView = view
        hostWindowManager = wm

        val timeout = Runnable { hide() }
        hideRunnable = timeout
        handler.postDelayed(timeout, HIDE_DELAY_MS)

        watchCallEnd()
    }

    fun hide() {
        hideRunnable?.let { handler.removeCallbacks(it) }
        hideRunnable = null
        unregisterCallState()
        val view = rootView ?: return
        rootView = null
        runCatching { (hostWindowManager ?: windowManager).removeView(view) }
        hostWindowManager = null
    }

    /** 接通 / 挂断立即撤窗，不依赖常驻服务；带首次快照守卫避免刚弹出就自关 */
    @Suppress("DEPRECATION")
    private fun watchCallEnd() {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return
        telephony = tm
        var firstSnapshot = true
        val listener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                if (state == TelephonyManager.CALL_STATE_RINGING) return
                // listen() 注册瞬间系统会立刻回调一次当前状态快照。
                // 来电在响铃前触发 show()，此刻往往是 IDLE——必须忽略这一次，
                // 否则卡片刚弹出就被这个"过期 IDLE"关掉。
                if (firstSnapshot) {
                    firstSnapshot = false
                    return
                }
                if (state == TelephonyManager.CALL_STATE_IDLE ||
                    state == TelephonyManager.CALL_STATE_OFFHOOK
                ) {
                    hide()
                }
            }
        }
        runCatching { tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE) }
            .onFailure { /* 权限或 ROM 限制：8 秒超时兜底仍有效 */ }
        callListener = listener
    }

    private var callListener: PhoneStateListener? = null

    @Suppress("DEPRECATION")
    private fun unregisterCallState() {
        val l = callListener ?: return
        callListener = null
        runCatching { telephony?.listen(l, PhoneStateListener.LISTEN_NONE) }
        telephony = null
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val HIDE_DELAY_MS = 8_000L
    }
}
