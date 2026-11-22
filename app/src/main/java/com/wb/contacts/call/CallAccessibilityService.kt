package com.wb.contacts.call

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍服务：只为了给来电卡片提供 TYPE_ACCESSIBILITY_OVERLAY 窗口。
 *
 * 为什么需要它（2026-11-23 realme RMX3888 真机逐层实测）：
 *  - 全屏 Activity：上屏 0.2 秒即被系统来电界面（OplusInCallActivity）顶回后台；
 *  - 普通悬浮窗 TYPE_APPLICATION_OVERLAY：锁屏下被锁屏层与来电界面压在下面，完全不可见；
 *  - 无障碍浮层 TYPE_ACCESSIBILITY_OVERLAY：层级在锁屏之上（TalkBack 高亮框同款），
 *    是普通应用唯一能稳定叠在来电界面之上的窗口类型。
 *
 * 隐私：本服务不读取屏幕内容（配置里 canRetrieveWindowContent=false），
 * 不解析、不记录、不上传任何事件，只对外暴露一个可承载高层级窗口的上下文。
 */
class CallAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "accessibility connected")
        // 无障碍一连接就开始自己听来电：不依赖 Telecom 是否绑定筛选服务
        CallCardPresenter.start(this)
    }

    /** 刻意留空：不处理任何屏幕事件 */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Log.i(TAG, "accessibility unbind")
        CallCardPresenter.stop()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        CallCardPresenter.stop()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallerIDDiag"

        /** 服务已连接时的实例；未开启无障碍时恒为 null */
        @Volatile
        var instance: CallAccessibilityService? = null
            private set

        fun isReady(): Boolean = instance != null
    }
}
