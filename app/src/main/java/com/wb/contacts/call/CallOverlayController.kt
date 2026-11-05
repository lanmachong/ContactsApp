package com.wb.contacts.call

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.wb.contacts.data.Contact

/**
 * 悬浮窗统一入口。
 * 系统来电筛选服务与后台监听服务都可能触发，这里做单例 + 主线程收口，
 * 避免重复 addView 导致 WindowManager 抛「already has a parent」。
 */
object CallOverlayController {

    private val main = Handler(Looper.getMainLooper())
    private var overlay: CallOverlay? = null

    fun init(context: Context) {
        if (overlay == null) {
            overlay = CallOverlay(context.applicationContext)
        }
    }

    fun canDraw(context: Context): Boolean {
        init(context)
        return overlay?.canDraw() == true
    }

    fun show(context: Context, number: String?, contact: Contact?) {
        init(context)
        val display = number?.takeIf { it.isNotBlank() } ?: contact?.phone ?: ""
        main.post { overlay?.show(contact, display) }
    }

    fun hide() {
        main.post { overlay?.hide() }
    }
}
