package com.wb.contacts.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 耗时任务的进度状态放在应用级单例，而不是 ViewModel 里。
 *
 * 以前这些字段是 ViewModel 的 mutableStateOf：锁屏后系统一旦回收 Activity，
 * ViewModel 跟着销毁，进度状态丢失；重新打开 App 会看到 busy=false，
 * 于是用户能在后台写入还没结束时又开始操作，造成并发写库。
 * 现在状态与界面生命周期解耦，前台服务和新的 ViewModel 都能读到同一份进度。
 */
object WorkState {

    private var _busy by mutableStateOf(false)

    var busy: Boolean
        get() = _busy
        set(value) {
            if (_busy == value) return
            _busy = value
            if (value) {
                busyStartedAt = System.currentTimeMillis()
                busyDone = 0
                busyTotal = 0
                busyPhase = ""
                busyEstimateSeconds = 0
            }
        }

    var busyText by mutableStateOf("")
    var busyPhase by mutableStateOf("")
    var busyDone by mutableStateOf(0)
    var busyTotal by mutableStateOf(0)
    var busyStartedAt by mutableStateOf(0L)
    var busyEstimateSeconds by mutableStateOf(0)
}
