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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 13+ 不给通知权限的话，前台服务的进度通知不会进通知抽屉，
        // 锁屏时用户就看不到「还剩多少」。保活本身不依赖它，但可见性依赖。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQ_NOTIFICATIONS
            )
        }
        pendingContactId = readContactId(intent)

        setContent {
            ContactsAppTheme {
                val vm: ContactsViewModel = viewModel()

                // 处理上万条时让用户直接看着进度走完，屏幕不会自己熄灭；
                // 用户手动锁屏 / 切后台时仍有 WorkGuardService + WakeLock 兜底。
                val keepScreenOn = vm.busy
                LaunchedEffect(keepScreenOn) {
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

    companion object {
        const val EXTRA_OPEN_CONTACT_ID = "extra_open_contact_id"
        private const val REQ_NOTIFICATIONS = 2001
    }
}
