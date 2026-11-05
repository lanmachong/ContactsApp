package com.wb.contacts.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wb.contacts.R
import com.wb.contacts.data.SmsMessage
import com.wb.contacts.util.PermissionUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScreen(vm: ContactsViewModel) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    var showPermissionHint by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        tick++
        vm.loadSms()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) vm.exportSmsCsv(uri)
    }

    OnResume {
        tick++
        vm.loadSms()
    }

    val canRead = remember(tick) { PermissionUtil.canReadSms(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.sms_title),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            pluralStringResource(R.plurals.sms_subtitle, vm.localSmsCount, vm.localSmsCount),
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
                stringResource(R.string.sms_info_title),
                stringResource(R.string.sms_info_desc)
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatPill(
                    stringResource(R.string.sms_stat_system),
                    pluralStringResource(R.plurals.sms_stat_count, vm.systemSmsCount, vm.systemSmsCount)
                )
                StatPill(
                    stringResource(R.string.sms_stat_local),
                    pluralStringResource(R.plurals.sms_stat_count, vm.localSmsCount, vm.localSmsCount)
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
                            // 只申请 READ_SMS。WRITE_SMS 是 signature 级权限，
                            // 普通应用申请不到，写权限通过「设为默认短信应用」获得。
                            permissionLauncher.launch(
                                arrayOf(android.Manifest.permission.READ_SMS)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.sms_grant_permission)) }
                } else {
                    Button(
                        onClick = { vm.importSms() },
                        enabled = !vm.busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (vm.busy) {
                                stringResource(R.string.action_importing)
                            } else {
                                stringResource(R.string.sms_import_system)
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())
                                .format(Date())
                            exportLauncher.launch("sms-$stamp.csv")
                        },
                        enabled = vm.localSmsCount > 0 && !vm.busy,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.sms_export_csv)) }
                }
            }

            if (!canRead) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.sms_privacy_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                stringResource(R.string.sms_section_manage),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(R.string.sms_restore_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.sms_section_backed_up),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.sms_list_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (vm.smsLoaded && vm.localSmsCount > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.sms_list_progress, vm.smsMessages.size, vm.localSmsCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))

            when {
                !vm.smsLoaded -> Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) { androidx.compose.material3.CircularProgressIndicator() }

                vm.smsMessages.isEmpty() -> EmptyHint(
                    if (canRead) {
                        stringResource(R.string.sms_empty_none)
                    } else {
                        stringResource(R.string.sms_empty_need_permission)
                    }
                )

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(420.dp)
                    ) {
                        items(vm.smsMessages, key = { it.id }) { sms ->
                            SmsRow(sms = sms)
                        }
                    }
                    if (vm.smsMessages.size >= 200) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { vm.loadSms(append = true) },
                            enabled = !vm.busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.action_load_more)) }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showPermissionHint) {
        AlertDialog(
            onDismissRequest = { showPermissionHint = false },
            title = { Text(stringResource(R.string.sms_permission_title)) },
            text = { Text(stringResource(R.string.sms_permission_body)) },
            confirmButton = {
                TextButton(onClick = { showPermissionHint = false }) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }
}

@Composable
private fun SmsRow(sms: SmsMessage) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val time = remember(sms.date) {
        runCatching {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(sms.date))
        }.getOrDefault("")
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (sms.isOutgoing()) Color(0xFFDCFCE7) else Color(0xFFDBEAFE),
                shape = CircleShape,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (sms.isOutgoing()) Icons.Default.Phone else Icons.Default.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (sms.isOutgoing()) Color(0xFF15803D) else Color(0xFF1D4ED8)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sms.address.ifBlank { stringResource(R.string.sms_unknown_number) },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = sms.typeLabel(context),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (expanded) sms.body else sms.body.replace('\n', ' '),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) 20 else 2
                )
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (sms.address.isNotBlank()) {
                IconButton(onClick = { ContactActions.dial(context, sms.address) }) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = stringResource(R.string.action_dial),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 58.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

@Composable
internal fun InfoCard(title: String, desc: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun ActionRow(
    title: String,
    desc: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun EmptyHint(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 警告卡：三个备份页与「清空数据」页共用同一套视觉 */
@Composable
internal fun WarningBox(title: String, body: String) {
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
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
