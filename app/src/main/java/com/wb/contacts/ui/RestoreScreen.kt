package com.wb.contacts.ui

import androidx.compose.foundation.clickable
import com.wb.contacts.util.PermissionUtil
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.TextButton
import com.wb.contacts.util.SmsRoleDiagnostics
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
 * · 勾选要还原的内容（通讯录 / 短信），默认一个都不勾
 * · 输入确认词才允许执行
 *
 * 写入前会先比对系统里已有的内容并跳过，不会把通讯录写成两份、短信写成两遍。
 * 短信需要本应用是默认短信应用；通话记录实测写不回系统（CallLog Provider 拒绝），
 * 所以这里不提供这一项 —— 能删不等于能写回去。
 *
 * 成为默认短信应用后角色不会自动交还，页面顶部会常驻提醒并给出切回入口。
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
    // 写回系统短信的唯一门槛：必须是「默认短信应用」。
    // WRITE_SMS 权限只是角色的伴随结果，别拿它当拦截条件 ——
    // 个别 ROM 上 checkSelfPermission 仍可能返回 false，会把已拿到身份的用户挡住。
    // 真写不进去时 SystemSms 会按 id 回读校验，如实计入失败数。
    val canWriteSms = remember(tick) { PermissionUtil.isDefaultSmsApp(context) }

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

            // 已经是默认短信应用时，把「不会自动交还」这件事放在最显眼的位置：
            // 用户常常设完就忘了切回，直到发现新短信没有通知才想起来。
            if (canWriteSms) {
                SmsRoleActiveNotice(switchBackSmsApp)
                Spacer(Modifier.height(18.dp))
            }

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
                ActionNotice(
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
                ActionNotice(
                    title = stringResource(R.string.restore_data_sms_role_title),
                    body = stringResource(R.string.restore_data_sms_role_body),
                    actionText = stringResource(R.string.restore_data_sms_role_action),
                    onAction = requestDefaultSmsApp
                )
                Spacer(Modifier.height(12.dp))
                // 系统设置里找不到本应用时，别让用户对着空列表猜
                SmsRoleCheckCard(tick)
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
 * 「默认短信应用列表里为什么没有本应用」的自检卡。
 *
 * 把 AOSP 筛候选用的四条查询原样跑一遍，逐项显示是否命中。
 * 这样能当场分清两种情况：
 * · 声明没写全 —— 那是我们的问题，装新版即可
 * · 四项都齐但列表仍没有 —— 那是厂商 ROM 的白名单，代码改不动
 */
@Composable
private fun SmsRoleCheckCard(tick: Int) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    // 展开时才查；从系统设置返回后（tick 变化）重新查一遍
    val report = remember(expanded, tick) {
        if (expanded) SmsRoleDiagnostics.check(context) else null
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.restore_data_sms_diag_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.restore_data_sms_diag_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            if (!expanded) {
                Button(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.restore_data_sms_diag_action)) }
            } else {
                val r = report
                if (r != null) {
                    DiagLine(stringResource(R.string.restore_data_sms_diag_item_sms), r.smsReceiver)
                    DiagLine(stringResource(R.string.restore_data_sms_diag_item_mms), r.mmsReceiver)
                    DiagLine(stringResource(R.string.restore_data_sms_diag_item_respond), r.respondService)
                    DiagLine(stringResource(R.string.restore_data_sms_diag_item_sendto), r.sendToActivity)

                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(
                            R.string.restore_data_sms_diag_status,
                            stringResource(
                                if (r.isDefault) {
                                    R.string.restore_data_sms_diag_default_yes
                                } else {
                                    R.string.restore_data_sms_diag_default_no
                                }
                            ),
                            stringResource(
                                if (r.roleAvailable) {
                                    R.string.restore_data_sms_diag_role_yes
                                } else {
                                    R.string.restore_data_sms_diag_role_no
                                }
                            ),
                            stringResource(
                                if (r.writeSmsGranted) {
                                    R.string.restore_data_sms_diag_perm_yes
                                } else {
                                    R.string.restore_data_sms_diag_perm_no
                                }
                            )
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            !r.qualified ->
                                stringResource(R.string.restore_data_sms_diag_verdict_missing)
                            !r.roleAvailable ->
                                stringResource(R.string.restore_data_sms_diag_verdict_no_role)
                            r.isDefault ->
                                stringResource(R.string.restore_data_sms_diag_verdict_ready)
                            else ->
                                stringResource(R.string.restore_data_sms_diag_verdict_rom)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(onClick = { expanded = false }) {
                    Text(stringResource(R.string.restore_data_sms_diag_hide))
                }
            }
        }
    }
}

@Composable
private fun DiagLine(label: String, ok: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = if (ok) "✓" else "✗",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (ok) Color(0xFF15803D) else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
