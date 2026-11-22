package com.wb.contacts.call

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import com.wb.contacts.ContactsApplication
import com.wb.contacts.data.Contact
import com.wb.contacts.util.PermissionUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 锁屏来电卡片的实际展示器，运行在 :a11y 进程。
 *
 * **卡片只有一路来源**：本进程自己听 CALL_STATE_RINGING（[startWatcher]），
 * 听到后从加密索引查姓名，直接画无障碍浮层。完全不依赖 Telecom 与主进程。
 *
 * 为什么不再走系统来电筛选服务（2026-11-23 realme RMX3888 真机日志实证）：
 * realme 的 Telecom 并非每次来电都绑定第三方筛选服务。实测 04:43:58、04:44:34、
 * 04:50:51、04:56:43 四次来电均无 SCREENING_BOUND，`FILTERING_COMPLETED` 只有
 * [Allow, logged, notified] 而缺 mCallScreeningAppName —— 电话根本没进筛选器，
 * 主进程也不会被拉起，我们连一个回调都收不到，无从补救。
 * 另一层原因是 realme 的进程冻结：锁屏后 OplusHansManager 冻结本应用，
 * 来电瞬间的解冻比 Telecom 的 bind 晚约 2 秒
 * （04:50:51.896 来电 → 04:50:53.950 才 unfreeze），bindService 起不了进程，同样跳过。
 *
 * 改为自监听后，05:05:56 与 05:06:32 两次锁屏来电均在 10~15ms 内完成上屏
 * （`show card source=watcher name=HIT a11y=true waits=0`），主进程全程未启动。
 * 顺带把「来电显示和骚扰拦截」这个角色从应用的权限诉求里彻底移除了。
 *
 * 本展示器由无障碍服务连接时启动，只要无障碍在，监听就在。
 * 号码与姓名只在内存与加密索引中使用，不落盘、不联网。
 */
object CallCardPresenter {

    private const val TAG = "CallerIDDiag"

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var overlay: CallOverlay? = null
    private var watcher: PhoneStateListener? = null
    private var appContext: Context? = null

    /** 每次显示请求的代号：新请求或隐藏时自增，用于丢弃过期的等待 */
    private var showGeneration = 0

    /** 当前是否有卡片在屏上 */
    private var shown = false

    /** 最近一次显示的号码与时刻，用于两路来源去重 */
    private var shownNumber: String? = null
    private var shownAt = 0L

    /** 无障碍服务连接就绪时调用：准备好悬浮窗并开始自己听来电 */
    fun start(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        if (overlay == null) overlay = CallOverlay(ctx)
        startWatcher(ctx)
    }

    /** 无障碍服务断开时调用 */
    fun stop() {
        stopWatcher()
        hideCard("a11y-off")
    }

    /**
     * 自己听来电状态，不依赖 Telecom 绑定。
     *
     * `onCallStateChanged` 的 phoneNumber 在 Android 9+ 只有持有 READ_CALL_LOG 时才返回；
     * 本应用已声明该权限（通话记录功能需要），取不到时放弃，避免弹出没有号码的卡片。
     */
    @Suppress("DEPRECATION")
    private fun startWatcher(context: Context) {
        if (watcher != null) return
        if (!PermissionUtil.canReadPhoneState(context)) {
            Log.w(TAG, "watcher skipped: no READ_PHONE_STATE")
            return
        }
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return
        val listener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                Log.i(TAG, "watcher state=$state hasNum=${!phoneNumber.isNullOrBlank()} shown=$shown")
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> {
                        val num = phoneNumber.orEmpty()
                        if (num.isBlank()) return
                        scope.launch {
                            (context.applicationContext as? ContactsApplication)?.awaitCallerIndex()
                            val contact = CallerIndex.lookup(num)
                            requestShow(
                                contact?.name.orEmpty(),
                                num,
                                contact?.avatarId ?: -1,
                                "watcher"
                            )
                        }
                    }
                    TelephonyManager.CALL_STATE_OFFHOOK,
                    TelephonyManager.CALL_STATE_IDLE -> if (shown) hideCard("watcher")
                }
            }
        }
        runCatching { tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE) }
            .onFailure { Log.w(TAG, "watcher listen failed: ${it.javaClass.simpleName}") }
        watcher = listener
        Log.i(TAG, "call watcher registered")
    }

    @Suppress("DEPRECATION")
    private fun stopWatcher() {
        val listener = watcher ?: return
        watcher = null
        val ctx = appContext ?: return
        val tm = ctx.getSystemService(TelephonyManager::class.java)
        runCatching { tm?.listen(listener, PhoneStateListener.LISTEN_NONE) }
    }

    /** 两路来源的统一入口：同一号码短时间内重复到达只显示一次 */
    fun requestShow(name: String, number: String, avatarId: Int, source: String) {
        val now = SystemClock.elapsedRealtime()
        if (shown && number.isNotEmpty() && number == shownNumber && now - shownAt < DEDUP_WINDOW_MS) {
            Log.i(TAG, "skip duplicate show source=$source")
            return
        }
        shownNumber = number
        shownAt = now
        showWhenReady(++showGeneration, name, number, avatarId, 0, source)
    }

    fun hideCard(source: String) {
        showGeneration++
        shown = false
        Log.i(TAG, "hide card source=$source")
        overlay?.hide()
    }

    /**
     * 等无障碍服务就绪再上屏。
     *
     * 进程刚被拉起时，无障碍框架把 [CallAccessibilityService] 挂上来要稍晚一点
     * （真机实测 0~1.9 秒）。若此刻直接 show，会退回 TYPE_APPLICATION_OVERLAY ——
     * 锁屏下被系统来电界面压住，用户什么都看不到。所以未就绪时短轮询等待，
     * 最多约 1.4 秒；用户压根没开无障碍时才在超时后走普通悬浮窗（解锁态仍可见）。
     */
    private fun showWhenReady(
        gen: Int,
        name: String,
        number: String,
        avatarId: Int,
        attempt: Int,
        source: String
    ) {
        if (gen != showGeneration) return
        val view = overlay ?: return
        val ready = CallAccessibilityService.isReady()
        if (ready || attempt >= A11Y_MAX_WAIT_STEPS) {
            Log.i(
                TAG,
                "show card source=$source name=${if (name.isBlank()) "MISS" else "HIT"} a11y=$ready waits=$attempt"
            )
            view.show(Contact(name = name, phone = number, avatarId = avatarId), number)
            shown = true
            return
        }
        handler.postDelayed(
            { showWhenReady(gen, name, number, avatarId, attempt + 1, source) },
            A11Y_WAIT_STEP_MS
        )
    }

    /** 等待无障碍就绪的轮询间隔与次数上限（120ms × 12 ≈ 1.4 秒） */
    private const val A11Y_WAIT_STEP_MS = 120L
    private const val A11Y_MAX_WAIT_STEPS = 12

    /** 两路来源的去重时间窗 */
    private const val DEDUP_WINDOW_MS = 3_000L
}
