package com.wb.contacts

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        pendingContactId = readContactId(intent)

        setContent {
            ContactsAppTheme {
                val vm: ContactsViewModel = viewModel()

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
    }
}
