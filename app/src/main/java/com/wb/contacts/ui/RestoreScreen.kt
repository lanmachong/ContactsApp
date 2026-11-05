package com.wb.contacts.ui

import androidx.compose.foundation.clickable
import com.wb.contacts.util.PermissionUtil
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wb.contacts.R

/**
 * 还原数据 —— 把本应用加密库里的内容写回手机系统。
 *
 * 与「清空数据」页互为镜像：一个往外删，一个往回写，入口都在首页右上角菜单里。
 *
 * · 勾选要还原的内容（通讯录 / 短信 / 通话记录），默认一个都不勾
 * · 输入确认词才允许执行
 *
 * 写入前会先比对系统里已有的内容并跳过，不会把通讯录写成两份、短信写成两遍。
 * 至于哪些内容写得回去，取决于系统的权限 —— 短信需要本应用是默认短信应用，
 * 通话记录则只有系统自带电话应用才能写，这里的提示会如实说明。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreDataScreen(vm: ContactsViewModel) {
    val keyword = stringResource(R.string.restore_data_confirm_keyword)
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }

    OnResume { tick++ }

    // 写回系统通讯录需要 WRITE_CONTACTS，没授权就先申请
    val canWriteContacts = remember(tick) { PermissionUtil.canWriteContacts(context) }
    // 写回系统短信的真正门槛：必须是「默认短信应用」。
    // WRITE_SMS 权限能拿到，但那不够 —— 不是默认短信应用时 insert 会
    // 静默失败（不报错、返回非 null Uri，短信却根本不在短信应用里）。
    val canWriteSms = remember(tick) {
        PermissionUtil.isDefaultSmsApp(context) && PermissionUtil.canWriteSms(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { tick++ }

    val requestDefaultSmsApp = rememberDefaultSmsAppLauncher(
        onReturn = { tick++ },
        onFail = { vm.notify(context.getString(R.string.sms_cannot_open_settings)) }
    )

    // 勾了要写回的内容，但权限没到位 —— 提前说清楚，别让用户点了才知道
    val needsContactsPerm = RestoreTarget.CONTACTS in vm.restoreTargets && !canWriteContacts
    val needsSmsRole = RestoreTarget.SMS in vm.restoreTargets && !canWriteSms

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.restore_data_title)) },
                navigationIcon = {
                    IconButton(onClick = vm::back) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(R.string.restore_data_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(18.dp))

            // —— 内容选择 ——
            Text(
                stringResource(R.string.restore_data_section_content),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            RestoreTargetRow(
                title = stringResource(R.string.clear_data_target_contacts),
                desc = stringResource(
                    R.string.restore_data_target_contacts_desc,
                    vm.localContactCount,
                    vm.systemContactCount
                ),
                checked = RestoreTarget.CONTACTS in vm.restoreTargets,
                onToggle = { vm.toggleRestoreTarget(RestoreTarget.CONTACTS, it) }
            )
            RestoreTargetRow(
                title = stringResource(R.string.clear_data_target_sms),
                desc = stringResource(
                    R.string.restore_data_target_sms_desc,
                    vm.localSmsCount,
                    vm.systemSmsCount
                ),
                checked = RestoreTarget.SMS in vm.restoreTargets,
                onToggle = { vm.toggleRestoreTarget(RestoreTarget.SMS, it) }
            )

            Spacer(Modifier.height(20.dp))

            // —— 权限预检：写不回去的提前说清楚 ——
            if (needsContactsPerm) {
                RestoreBlockNotice(
                    title = stringResource(R.string.restore_data_contacts_perm_title),
                    body = stringResource(R.string.restore_data_contacts_perm_body),
                    actionText = stringResource(R.string.restore_data_contacts_perm_action),
                    onAction = {
                        permissionLauncher.launch(
                            arrayOf(android.Manifest.permission.WRITE_CONTACTS)
                        )
                    }
                )
                Spacer(Modifier.height(12.dp))
            }
            if (needsSmsRole) {
                RestoreBlockNotice(
                    title = stringResource(R.string.restore_data_sms_role_title),
                    body = stringResource(R.string.restore_data_sms_role_body),
                    actionText = stringResource(R.string.restore_data_sms_role_action),
                    onAction = requestDefaultSmsApp
                )
                Spacer(Modifier.height(12.dp))
            }

            // —— 确认输入 ——
            Text(
                stringResource(R.string.restore_data_confirm_label, keyword),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.restoreConfirmInput,
                onValueChange = vm::onRestoreConfirmChange,
                singleLine = true,
                placeholder = { Text(keyword) },
                modifier = Modifier.fillMaxWidth()
            )
            if (vm.restoreConfirmInput.isNotEmpty() && !vm.restoreConfirmMatched(keyword)) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.restore_data_confirm_mismatch, keyword),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { vm.executeRestore() },
                enabled = !vm.busy && vm.restoreReady(keyword),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.restore_data_action)) }

            if (vm.restoreTargets.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.restore_data_need_select),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(20.dp))

            WarningBox(
                stringResource(R.string.restore_data_warning_title),
                stringResource(R.string.restore_data_warning_body)
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RestoreTargetRow(
    title: String,
    desc: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onToggle,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (checked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 权限 / 身份预检提示。
 *
 * 写回系统这件事，不同内容要的东西不一样：通讯录要写权限，短信要默认短信应用身份。
 * 这里提前说清楚并给出跳转按钮，别让用户写完了才发现被系统拒绝。
 */
@Composable
private fun RestoreBlockNotice(
    title: String,
    body: String,
    actionText: String,
    onAction: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFB45309),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth()
            ) { Text(actionText) }
        }
    }
}
