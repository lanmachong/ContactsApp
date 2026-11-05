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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wb.contacts.data.SmsMessage
import com.wb.contacts.util.PermissionUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** WRITE_SMS 不在公开 SDK 中（signature 级权限），只能用字面量 */
private const val PERM_WRITE_SMS = "android.permission.WRITE_SMS"

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
    val canWrite = remember(tick) { PermissionUtil.canWriteSms(context) }
    val isDefaultSms = remember(tick) { PermissionUtil.isDefaultSmsApp(context) }

    val defaultSmsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        tick++
        vm.loadSms()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("短信备份", fontWeight = FontWeight.Bold)
                        Text(
                            "${vm.smsMessages.size} 条已加密保存",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = vm::back) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
                "把手机短信搬进加密库",
                "整条短信用 AES-256-GCM 加密后写入本机数据库，库里没有任何明文内容。" +
                    "系统里的原短信可以随后清掉，防流氓软件读取。"
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatPill("系统短信", "${vm.systemSmsCount} 条")
                StatPill("本应用", "${vm.smsMessages.size} 条")
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!canRead) {
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.READ_SMS,
                                    // WRITE_SMS 是 signature 级权限，不在公开 SDK 里，
                                    // 普通应用申请不到；写权限通过「设为默认短信应用」获得。
                                    PERM_WRITE_SMS
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("授予短信权限") }
                } else {
                    Button(
                        onClick = { vm.importSms() },
                        enabled = !vm.busy,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (vm.busy) "导入中…" else "导入系统短信") }
                    OutlinedButton(
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())
                                .format(Date())
                            exportLauncher.launch("sms-$stamp.csv")
                        },
                        enabled = vm.smsMessages.isNotEmpty() && !vm.busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("导出 CSV") }
                }
            }

            if (!canRead) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "短信内容属于敏感数据，只有你授权后本应用才会读取。" +
                        "所有数据仅存在这台手机上，不上传、不联网。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "管理",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            ActionRow(
                title = "导出到系统短信",
                desc = "把本应用里的短信写回手机短信应用，按内容+时间去重",
                enabled = canWrite && vm.smsMessages.isNotEmpty() && !vm.busy,
                onClick = { vm.exportSmsToSystem() }
            )
            ActionRow(
                title = "清空系统短信",
                desc = if (isDefaultSms) {
                    "删除手机短信应用里的全部短信，本应用的加密副本保留"
                } else {
                    "需要先把本应用设为默认短信应用（Android 4.4 起的系统限制）"
                },
                enabled = canWrite && !vm.busy,
                danger = true,
                onClick = {
                    if (isDefaultSms) {
                        vm.requestWipeSms()
                    } else {
                        val intent = PermissionUtil.defaultSmsAppIntent(context)
                        if (intent != null) {
                            runCatching { defaultSmsLauncher.launch(intent) }
                                .onFailure { vm.notify("无法打开默认短信应用设置") }
                        } else {
                            vm.notify("本机不支持切换默认短信应用")
                        }
                    }
                }
            )
            ActionRow(
                title = "清空本应用短信副本",
                desc = "删除本应用保存的短信副本，系统里的原短信不受影响",
                enabled = vm.smsMessages.isNotEmpty() && !vm.busy,
                danger = true,
                onClick = { vm.requestClearSms() }
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "已备份的短信",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "点击可展开全文，长按号码可直接拨号。列表按导入顺序倒序展示，最多加载 200 条。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            when {
                !vm.smsLoaded -> Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) { androidx.compose.material3.CircularProgressIndicator() }

                vm.smsMessages.isEmpty() -> EmptyHint(
                    if (canRead) "还没有备份任何短信" else "需要短信读取权限才能显示内容"
                )

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(420.dp)
                    ) {
                        items(vm.smsMessages, key = { it.id }) { sms ->
                            SmsRow(
                                sms = sms,
                                onDelete = { vm.deleteSms(sms.id) }
                            )
                        }
                    }
                    if (vm.smsMessages.size >= 200) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { vm.loadSms(append = true) },
                            enabled = !vm.busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("加载更多") }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showPermissionHint) {
        AlertDialog(
            onDismissRequest = { showPermissionHint = false },
            title = { Text("短信权限说明") },
            text = {
                Text(
                    "读取短信需要 READ_SMS 权限，删除短信需要 WRITE_SMS 权限。\n\n" +
                        "授予后，只有本应用能读到短信内容，且数据全部加密存在本机。"
                )
            },
            confirmButton = {
                TextButton(onClick = { showPermissionHint = false }) { Text("知道了") }
            }
        )
    }
}

@Composable
private fun SmsRow(sms: SmsMessage, onDelete: () -> Unit) {
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
                        text = sms.address.ifBlank { "未知号码" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = sms.typeLabel(),
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
                        contentDescription = "拨号",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.outline
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
    danger: Boolean = false,
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
                color = if (danger) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
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
