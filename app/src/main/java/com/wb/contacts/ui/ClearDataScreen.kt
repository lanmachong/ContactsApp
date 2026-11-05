package com.wb.contacts.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wb.contacts.R
import com.wb.contacts.util.PermissionUtil

/**
 * 清空数据 —— 所有删除操作的唯一入口。
 *
 * 之前删除散落在四个地方：首页菜单、导入页第 4 步、短信页、通话记录页，
 * 每处的措辞和后果说明都不一样，用户很难判断自己删的到底是哪一份数据。
 * 现在集中到这一页：
 *
 * · 选方向：清空系统里的，还是清空本应用里的加密副本
 * · 勾选要清空的内容（短信 / 通话记录 / 通讯录），默认一个都不勾
 * · 输入确认词才允许执行 —— 删除不可撤销，值得多按一次键盘
 *
 * 「清空系统里的」三项都能勾，但各有前置条件：
 * 短信要本应用是默认短信应用，通话记录要 WRITE_CALL_LOG 运行时权限。
 * v1.8.0 曾把后两项一刀切禁用，实际挡住了本来删得掉的机器 ——
 * 现在改成提前提示 + 给跳转，删不掉时由 ViewModel 回读条数如实回报。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClearDataScreen(vm: ContactsViewModel) {
    val keyword = stringResource(R.string.clear_data_confirm_keyword)
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    OnResume { tick++ }

    val clearingSystem = vm.clearDirection == ClearDirection.SYSTEM
    val isDefaultSms = remember(tick) { PermissionUtil.isDefaultSmsApp(context) }
    val canWriteCallLog = remember(tick) { PermissionUtil.canWriteCallLog(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { tick++ }
    val requestDefaultSmsApp = rememberDefaultSmsAppLauncher(
        onReturn = { tick++ },
        onFail = { vm.notify(context.getString(R.string.sms_cannot_open_settings)) }
    )
    val switchBackSmsApp = rememberSwitchBackSmsLauncher(
        onReturn = { tick++ },
        onFail = { vm.notify(context.getString(R.string.sms_cannot_open_settings)) }
    )

    // 只有「清空系统里的」方向才需要检查系统权限与角色
    val needsSmsRole = clearingSystem && ClearTarget.SMS in vm.clearTargets && !isDefaultSms
    val needsCallLogPerm = clearingSystem && ClearTarget.CALLS in vm.clearTargets && !canWriteCallLog

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.clear_data_title)) },
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
                stringResource(R.string.clear_data_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(18.dp))

            // 只要本应用还占着默认短信角色就常驻提醒，别让用户设完忘了切回
            if (isDefaultSms) {
                SmsRoleActiveNotice(switchBackSmsApp)
                Spacer(Modifier.height(18.dp))
            }

            // —— 方向选择 ——
            // 放在内容选择之前：方向决定了每项需要什么前置条件
            Text(
                stringResource(R.string.clear_data_section_direction),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            ClearDirectionRow(
                title = stringResource(R.string.clear_data_direction_system),
                desc = stringResource(R.string.clear_data_direction_system_desc),
                selected = vm.clearDirection == ClearDirection.SYSTEM,
                onSelect = { vm.selectClearDirection(ClearDirection.SYSTEM) }
            )
            ClearDirectionRow(
                title = stringResource(R.string.clear_data_direction_local),
                desc = stringResource(R.string.clear_data_direction_local_desc),
                selected = vm.clearDirection == ClearDirection.LOCAL,
                onSelect = { vm.selectClearDirection(ClearDirection.LOCAL) }
            )

            Spacer(Modifier.height(20.dp))

            // —— 内容选择 ——
            Text(
                stringResource(R.string.clear_data_section_content),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            ClearTargetRow(
                title = stringResource(R.string.clear_data_target_contacts),
                desc = stringResource(
                    R.string.clear_data_target_contacts_desc,
                    vm.systemContactCount,
                    vm.localContactCount
                ),
                checked = ClearTarget.CONTACTS in vm.clearTargets,
                onToggle = { vm.toggleClearTarget(ClearTarget.CONTACTS, it) }
            )
            ClearTargetRow(
                title = stringResource(R.string.clear_data_target_sms),
                desc = stringResource(
                    R.string.clear_data_target_sms_desc,
                    vm.systemSmsCount,
                    vm.localSmsCount
                ),
                checked = ClearTarget.SMS in vm.clearTargets,
                onToggle = { vm.toggleClearTarget(ClearTarget.SMS, it) },
                note = if (clearingSystem) {
                    stringResource(R.string.clear_data_sms_system_note)
                } else {
                    null
                }
            )
            ClearTargetRow(
                title = stringResource(R.string.clear_data_target_calls),
                desc = stringResource(
                    R.string.clear_data_target_calls_desc,
                    vm.systemCallCount,
                    vm.localCallCount
                ),
                checked = ClearTarget.CALLS in vm.clearTargets,
                onToggle = { vm.toggleClearTarget(ClearTarget.CALLS, it) },
                note = if (clearingSystem) {
                    stringResource(R.string.clear_data_calls_system_note)
                } else {
                    null
                }
            )

            // —— 前置条件提示 ——
            if (needsSmsRole) {
                Spacer(Modifier.height(12.dp))
                ActionNotice(
                    title = stringResource(R.string.clear_data_sms_role_title),
                    body = stringResource(R.string.clear_data_sms_role_body),
                    actionText = stringResource(R.string.restore_data_sms_role_action),
                    onAction = requestDefaultSmsApp
                )
            }
            if (needsCallLogPerm) {
                Spacer(Modifier.height(12.dp))
                ActionNotice(
                    title = stringResource(R.string.clear_data_calls_perm_title),
                    body = stringResource(R.string.clear_data_calls_perm_body),
                    actionText = stringResource(R.string.clear_data_calls_perm_action),
                    onAction = {
                        permissionLauncher.launch(
                            arrayOf(android.Manifest.permission.WRITE_CALL_LOG)
                        )
                    }
                )
            }

            Spacer(Modifier.height(20.dp))

            // —— 确认输入 ——
            Text(
                stringResource(R.string.clear_data_confirm_label, keyword),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.clearConfirmInput,
                onValueChange = vm::onClearConfirmChange,
                singleLine = true,
                placeholder = { Text(keyword) },
                modifier = Modifier.fillMaxWidth()
            )
            if (vm.clearConfirmInput.isNotEmpty() && !vm.clearConfirmMatched(keyword)) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.clear_data_confirm_mismatch, keyword),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { vm.executeClear() },
                enabled = !vm.busy && vm.clearReady(keyword),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.clear_data_action)) }

            if (vm.clearTargets.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.clear_data_need_select),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(20.dp))

            WarningBox(
                stringResource(R.string.clear_data_warning_title),
                stringResource(R.string.clear_data_warning_body)
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ClearTargetRow(
    title: String,
    desc: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    /** 可选条件下的前置说明，不影响是否可勾选 */
    note: String? = null
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
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ClearDirectionRow(
    title: String,
    desc: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
