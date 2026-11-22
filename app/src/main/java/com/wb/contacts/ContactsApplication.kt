package com.wb.contacts

import android.app.Application
import android.os.Build
import android.util.Log
import com.wb.contacts.call.CallNotifier
import com.wb.contacts.call.CallOverlayController
import com.wb.contacts.call.CallerIndex
import com.wb.contacts.data.ArchiveRepository
import com.wb.contacts.data.ContactsRepository
import com.wb.contacts.data.Crypto
import com.wb.contacts.data.CallerIndexStore
import com.wb.contacts.ui.WorkState
import com.wb.contacts.work.WorkGuardService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ContactsApplication : Application() {

    /** 全局唯一仓储（数据库 + 加解密） */
    val repository: ContactsRepository by lazy { ContactsRepository(this) }

    /** 短信 / 通话记录仓储，与 ViewModel 共用实例 */
    val archiveRepository: ArchiveRepository by lazy { ArchiveRepository(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 来电索引装载完成的信号。
     *
     * 进程冷启动时（来电识别服务被系统拉起、App 界面从未打开），索引是空的，
     * 需要解密加密库才能建。来电回调有时限不能同步等，所以 expose 一个可等待的
     * CompletableDeferred：解密成功或失败都会 complete，避免调用方永久挂起。
     */
    private val callerIndexReady = CompletableDeferred<Unit>()

    /** 保证预热只启动一次：onCreate 与来电回调用到同一份 */
    @Volatile
    private var indexWarmStarted = false

    /** 任务意外抛异常时也要收干净遮罩与前台服务，别让进度层永远挡着 */
    private val workFailureHandler = CoroutineExceptionHandler { _, _ ->
        WorkState.busy = false
        WorkState.busyText = ""
        WorkState.busyPhase = ""
        WorkState.busyDone = 0
        WorkState.busyTotal = 0
        WorkGuardService.stop(this)
    }

    /**
     * 大批量写入专用协程域：不绑 ViewModel / Activity。
     * 锁屏后系统回收 Activity 时，viewModelScope 会被取消、写到一半的数据就断了；
     * 这里让任务跟进程走，配合 WorkGuardService 一直跑到结束。
     */
    val workScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + workFailureHandler
    )

    override fun onCreate() {
        super.onCreate()
        instance = this
        // :a11y 进程只承载无障碍浮层与来电监听，不需要通知渠道和主进程的悬浮窗单例
        if (isMainProcess()) {
            CallNotifier.ensureChannel(this)
            CallOverlayController.init(this)
        }
        // 提前把 Keystore 密钥准备好，别让首屏等硬件
        scope.launch { runCatching { Crypto.warmUp() } }
        warmUpCallerIndex()
    }

    private fun isMainProcess(): Boolean {
        val name = getProcessNameCompat()
        return name == null || name == packageName
    }

    private fun getProcessNameCompat(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            null
        }

    /**
     * 把加密库里的号码解密后建到内存索引里（幂等，多次调用只装载一次）。
     * 来电时需要在几十毫秒内完成匹配，不能等数据库查询。
     */
    fun warmUpCallerIndex() {
        if (indexWarmStarted) return
        indexWarmStarted = true
        scope.launch {
            val t0 = System.currentTimeMillis()
            // 优先从加密索引文件一次解密加载（毫秒级），冷启动被系统拉起时
            // 进程随时可能被回收，绝不能走逐条解密全库那条 1～2 秒的慢路。
            val cached = CallerIndexStore.load(this@ContactsApplication)
            if (cached != null) {
                CallerIndex.update(cached)
                Log.i(TAG, "index from store count=${cached.size} cost=${System.currentTimeMillis() - t0}ms")
            } else {
                runCatching {
                    val all = repository.getAll()
                    CallerIndex.update(all)
                    Log.i(TAG, "index from db count=${all.size} cost=${System.currentTimeMillis() - t0}ms")
                }.onFailure { Log.e(TAG, "index decrypt FAILED: ${it.javaClass.simpleName}", it) }
            }
            // 成功失败都放行，避免来电方等一个永远不来的信号
            if (!callerIndexReady.isCompleted) callerIndexReady.complete(Unit)
        }
    }

    /**
     * 供来电识别等待索引就绪。冷启动首次来电索引为空时，
     * 先 respondToCall 放行，再 await 本信号，解密完即可拿到姓名重弹卡片。
     * 已就绪时 suspend 立即返回，不产生额外开销。
     */
    suspend fun awaitCallerIndex() {
        warmUpCallerIndex()
        callerIndexReady.await()
    }

    companion object {
        private const val TAG = "CallerIDDiag"
        @Volatile
        lateinit var instance: ContactsApplication
            private set
    }
}
