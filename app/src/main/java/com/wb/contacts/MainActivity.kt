package com.wb.contacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wb.contacts.ui.ContactsAppTheme
import com.wb.contacts.ui.ContactsRoot
import com.wb.contacts.ui.ContactsViewModel

class MainActivity : ComponentActivity() {

    /** 悬浮窗点「查看」跳转过来时带上的联系人 id */
    private var pendingContactId by mutableStateOf<Long?>(null)

    /** 通知权限只问一次，避免每次批量任务都弹 */
    private var notificationAsked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingContactId = readContactId(intent)

        setContent {
            ContactsAppTheme {
                val vm: ContactsViewModel = viewModel()

                // 处理上万条时让用户直接看着进度走完，屏幕不会自己熄灭；
                // 用户手动锁屏 / 切后台时仍有 WorkGuardService + WakeLock 兜底。
                val keepScreenOn = vm.busy
                LaunchedEffect(keepScreenOn) {
                    // 通知权限只在批量任务真正开跑时才要：此时用户能理解
                    // 「需要通知」是因为要看进度。打开 App 就弹权限属于不必要的索要。
                    if (keepScreenOn) ensureNotificationPermission()
                    if (keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                val target = pendingContactId
                LaunchedEffect(target) {
                    if (target != null) {
                        vm.refresh()
                        vm.openDetail(target)
                        pendingContactId = null
                    }
                }

                ContactsRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readContactId(intent)?.let { pendingContactId = it }
    }

    private fun readContactId(intent: Intent?): Long? =
        intent?.getLongExtra(EXTRA_OPEN_CONTACT_ID, 0L)?.takeIf { it > 0L }

    /**
     * 批量导入 / 还原 / 清空正在运行时才索要通知权限。
     *
     * 没有这个权限，前台服务照样能跑（保活不依赖它），只是进度通知不进通知抽屉、
     * 切到后台看不到还剩多少。所以它属于「体验增强」而非必需项，
     * 不该在 App 一启动就打断用户。
     */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (notificationAsked) return
        notificationAsked = true
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
    }

    companion object {
        const val EXTRA_OPEN_CONTACT_ID = "extra_open_contact_id"
        private const val REQ_NOTIFICATIONS = 2001
    }
}
