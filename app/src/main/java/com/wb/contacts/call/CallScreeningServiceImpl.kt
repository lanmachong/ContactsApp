package com.wb.contacts.call

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import com.wb.contacts.ContactsApplication
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 系统来电筛选服务。
 * Android 10+ 在用户授予「来电显示和骚扰拦截」角色后生效，
 * 能在振铃瞬间拿到来电号码（比 PhoneStateListener 稳定得多）。
 *
 * 本服务只做识别与展示，不拦截任何来电：始终 respondToCall 放行。
 */
class CallScreeningServiceImpl : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        // getCallDirection 是 API 29 才有的；低版本 onScreenCall 只会被来电触发
        val incoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        } else {
            true
        }

        if (incoming) {
            val app = application as? ContactsApplication
            if (app != null) {
                ensureIndexReady(app)
                val contact = CallerIndex.lookup(number)
                if (CallOverlayController.canDraw(this)) {
                    CallOverlayController.show(this, number, contact)
                }
            }
        }

        // 无论是否识别到，都放行，绝不静默或拒接
        respondToCall(callDetails, CallResponse.Builder().build())
    }

    /**
     * 进程被系统拉起时内存索引是空的，这里做一次带超时的同步加载。
     * 超时也要放行，不能因为查不到名字就耽误接电话。
     */
    private fun ensureIndexReady(app: ContactsApplication) {
        if (CallerIndex.size > 0) return
        val latch = CountDownLatch(1)
        Thread {
            runCatching {
                runBlocking { CallerIndex.update(app.repository.getAll()) }
            }
            latch.countDown()
        }.start()
        runCatching { latch.await(1500, TimeUnit.MILLISECONDS) }
    }
}
