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
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wb.contacts.R
import com.wb.contacts.data.CallRecord
import com.wb.contacts.util.PermissionUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogScreen(vm: ContactsViewModel) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        tick++
        vm.loadCallLog()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) vm.exportCallsCsv(uri)
    }

    OnResume {
        tick++
        vm.loadCallLog()
    }

    val canRead = remember(tick) { PermissionUtil.canReadCallLog(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.calllog_title),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            pluralStringResource(R.plurals.calllog_subtitle, vm.localCallCount, vm.localCallCount),
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
                stringResource(R.string.calllog_info_title),
                stringResource(R.string.calllog_info_desc)
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatPill(
                    stringResource(R.string.calllog_stat_system),
                    pluralStringResource(R.plurals.calllog_stat_count, vm.systemCallCount, vm.systemCallCount)
                )
                StatPill(
                    stringResource(R.string.calllog_stat_local),
                    pluralStringResource(R.plurals.calllog_stat_count, vm.localCallCount, vm.localCallCount)
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
                            // 只申请 READ_CALL_LOG。WRITE_CALL_LOG 是 signature|privileged 权限，
                            // 普通应用永远拿不到，申请了也没有意义。
                            permissionLauncher.launch(
                                arrayOf(android.Manifest.permission.READ_CALL_LOG)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.calllog_grant_permission)) }
                } else {
                    Button(
                        onClick = { vm.importCallLog() },
                        enabled = !vm.busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (vm.busy) {
                                stringResource(R.string.action_importing)
                            } else {
                                stringResource(R.string.calllog_import_system)
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())
                                .format(Date())
                            exportLauncher.launch("calls-$stamp.csv")
                        },
                        enabled = vm.localCallCount > 0 && !vm.busy,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.calllog_export_csv)) }
                }
            }

            if (!canRead) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.calllog_privacy_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                stringResource(R.string.calllog_section_manage),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(R.string.calllog_manage_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.calllog_section_backed_up),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.calllog_list_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (vm.callLogLoaded && vm.localCallCount > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.calllog_list_progress, vm.callRecords.size, vm.localCallCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))

            when {
                !vm.callLogLoaded -> Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) { androidx.compose.material3.CircularProgressIndicator() }

                vm.callRecords.isEmpty() -> EmptyHint(
                    if (canRead) {
                        stringResource(R.string.calllog_empty_none)
                    } else {
                        stringResource(R.string.calllog_empty_need_permission)
                    }
                )

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(420.dp)
                    ) {
                        items(vm.callRecords, key = { it.id }) { record ->
                            CallRecordRow(record = record)
                        }
                    }
                    if (vm.callRecords.size >= 200) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { vm.loadCallLog(append = true) },
                            enabled = !vm.busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.action_load_more)) }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CallRecordRow(record: CallRecord) {
    val context = LocalContext.current
    val time = remember(record.date) {
        runCatching {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(record.date))
        }.getOrDefault("")
    }
    val icon = when (record.type) {
        1, 6 -> Icons.Default.CallReceived
        2 -> Icons.Default.CallMade
        3 -> Icons.Default.CallMissed
        else -> Icons.Default.Phone
    }
    val tint = when {
        record.isMissed() -> Color(0xFFDC2626)
        record.isIncoming() -> Color(0xFF15803D)
        else -> Color(0xFF1D4ED8)
    }
    val displayName = record.contactName.ifBlank {
        record.number.ifBlank { stringResource(R.string.calllog_unknown_number) }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = tint.copy(alpha = 0.12f), shape = CircleShape, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        if (record.number.isNotBlank()) ContactActions.dial(context, record.number)
                    }
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = buildString {
                        if (record.contactName.isNotBlank() && record.number.isNotBlank()) {
                            append(record.number)
                            append(" · ")
                        }
                        append(record.typeLabel(context))
                        val d = record.durationLabel(context)
                        if (d.isNotEmpty()) {
                            append(" · ")
                            append(d)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
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
