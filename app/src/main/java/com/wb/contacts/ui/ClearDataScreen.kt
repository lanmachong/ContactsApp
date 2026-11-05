package com.wb.contacts.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wb.contacts.R

/**
 * 清空数据 —— 所有删除操作的唯一入口。
 *
 * 之前删除散落在四个地方：首页菜单、导入页第 4 步、短信页、通话记录页，
 * 每处的措辞和后果说明都不一样，用户很难判断自己删的到底是哪一份数据。
 * 现在集中到这一页：
 *
 * · 勾选要清空的内容（短信 / 通话记录 / 通讯录），默认一个都不勾
 * · 选方向：清空系统里的，还是清空本应用里的加密副本
 * · 输入确认词才允许执行 —— 删除不可撤销，值得多按一次键盘
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClearDataScreen(vm: ContactsViewModel) {
    val keyword = stringResource(R.string.clear_data_confirm_keyword)

    // 清系统里的原始数据时，只有通讯录是系统真正允许普通应用删的：
    // 短信要默认短信应用身份，通话记录系统根本不放行。
    // 与其让用户勾了再失败，不如在方向选定的当下就禁用掉，并把原因写清楚。
    val clearingSystem = vm.clearDirection == ClearDirection.SYSTEM

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

            // —— 方向选择 ——
            // 放在内容选择之前：方向决定了哪些内容真的能选，
            // 先选方向再选内容，用户才不会先勾了才发现被禁用。
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
                disabledReason = if (clearingSystem) {
                    stringResource(R.string.clear_data_sms_system_blocked)
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
                disabledReason = if (clearingSystem) {
                    stringResource(R.string.clear_data_calls_system_blocked)
                } else {
                    null
                }
            )

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
    /** 不可选时的原因；非空则这一行被禁用 */
    disabledReason: String? = null
) {
    val enabled = disabledReason == null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onToggle(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onToggle,
            enabled = enabled,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    disabledReason != null -> MaterialTheme.colorScheme.onSurfaceVariant
                    checked -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 不可选的原因直接写在下面，别让用户以为是自己哪里点错了
            if (disabledReason != null) {
                Text(
                    text = disabledReason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
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
) {    Row(
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

