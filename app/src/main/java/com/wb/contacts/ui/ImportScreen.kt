package com.wb.contacts.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wb.contacts.R
import com.wb.contacts.util.PermissionUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 通讯录备份。
 *
 * 页面骨架与「短信备份」「通话记录备份」完全一致：
 *   说明卡 → 统计（系统 / 本应用）→ 主操作按钮 → 管理 → 已备份列表说明 → 提示卡
 *
 * 这里不放任何删除入口 —— 所有清空操作统一收在首页右上角菜单的「清空数据」页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(vm: ContactsViewModel) {
    val context = LocalContext.current
    val contacts by vm.contacts.collectAsState()
    var tick by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        tick++
        vm.scanSystemContacts()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) vm.exportCsv(uri)
    }

    OnResume {
        tick++
        vm.scanSystemContacts()
    }

    val canRead = remember(tick) { PermissionUtil.canReadContacts(context) }
    val canWrite = remember(tick) { PermissionUtil.canWriteContacts(context) }
    val versionName = remember {
        runCatching {
            context.applicationContext.packageManager
                .getPackageInfo(context.applicationContext.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.import_title),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            pluralStringResource(R.plurals.import_subtitle, contacts.size, contacts.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
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

            InfoCard(
                stringResource(R.string.import_info_title),
                stringResource(R.string.import_info_desc)
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatPill(
                    stringResource(R.string.import_stat_system),
                    if (vm.systemScanned) {
                        pluralStringResource(R.plurals.import_stat_count, vm.systemContactCount, vm.systemContactCount)
                    } else {
                        stringResource(R.string.import_stat_pending)
                    }
                )
                StatPill(
                    stringResource(R.string.import_stat_local),
                    pluralStringResource(R.plurals.import_stat_count, contacts.size, contacts.size)
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!canRead) {
                    Button(
                        onClick = {
                            // 导入只需要读权限；写权限等到导出到系统时才单独申请
                            permissionLauncher.launch(
                                arrayOf(android.Manifest.permission.READ_CONTACTS)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.import_grant_permission)) }
                } else {
                    Button(
                        onClick = { vm.importFromSystem() },
                        enabled = !vm.busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (vm.busy) {
                                stringResource(R.string.action_importing)
                            } else {
                                stringResource(R.string.import_start)
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = { vm.markDuplicates() },
                        enabled = !vm.busy && contacts.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.import_check_duplicates)) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.import_duplicate_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())
                            .format(Date())
                        exportLauncher.launch("contacts-$stamp.csv")
                    },
                    enabled = contacts.isNotEmpty() && !vm.busy
                ) { Text(stringResource(R.string.import_export_csv)) }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                stringResource(R.string.import_section_manage),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            ActionRow(
                title = if (canWrite) {
                    stringResource(R.string.import_action_export_system)
                } else {
                    stringResource(R.string.import_grant_write)
                },
                desc = stringResource(R.string.import_action_export_system_desc),
                enabled = !vm.busy && contacts.isNotEmpty(),
                onClick = {
                    if (!canWrite) {
                        permissionLauncher.launch(
                            arrayOf(android.Manifest.permission.WRITE_CONTACTS)
                        )
                    } else {
                        vm.exportToSystem()
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.import_section_backed_up),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.import_list_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            WarningBox(
                stringResource(R.string.import_warning_title),
                stringResource(R.string.import_warning_body)
            )

            Spacer(Modifier.height(16.dp))
            Text(
                if (versionName.isBlank()) {
                    stringResource(R.string.import_local_only)
                } else {
                    stringResource(R.string.import_version_local, versionName)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
internal fun StatPill(label: String, value: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "$label $value",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
