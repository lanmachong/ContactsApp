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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.collectAsState
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
import com.wb.contacts.util.PermissionUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                title = { Text("导入与清理") },
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

            StepCard(
                index = 1,
                title = "导入系统通讯录",
                desc = "一次性把手机通讯录读进本应用，整条记录用 AES-256-GCM 加密后写入本机数据库。"
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 44.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatPill("系统通讯录", if (vm.systemScanned) "${vm.systemContactCount} 条" else "待授权")
                StatPill("本应用", "${contacts.size} 条")
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 44.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!canRead) {
                    Button(
                        onClick = {
                            // 导入只需要读权限；写权限等到导出/清理时再单独申请
                            permissionLauncher.launch(
                                arrayOf(android.Manifest.permission.READ_CONTACTS)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("授予通讯录权限") }
                } else {
                    Button(
                        onClick = { vm.importFromSystem() },
                        enabled = !vm.busy,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (vm.busy) "导入中…" else "开始导入") }
                    OutlinedButton(
                        onClick = { vm.markDuplicates() },
                        enabled = !vm.busy && contacts.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("检测重复") }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "重复的号码会被自动打上「重复」标签，搜号码即可定位；系统通讯录里的重复卡片需要你自己去系统里删。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 44.dp)
            )

            Spacer(Modifier.height(20.dp))

            StepCard(
                index = 2,
                title = "先备份，再动手",
                desc = "CSV 是明文文件，只建议临时存放。导出后请自己保管好，不想要了就删掉。"
            )
            Row(modifier = Modifier.fillMaxWidth().padding(start = 44.dp)) {
                OutlinedButton(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())
                            .format(Date())
                        exportLauncher.launch("contacts-$stamp.csv")
                    },
                    enabled = contacts.isNotEmpty() && !vm.busy
                ) { Text("导出 CSV 备份") }
            }

            Spacer(Modifier.height(20.dp))

            StepCard(
                index = 3,
                title = "导出到系统通讯录",
                desc = "把本应用里的联系人写回手机系统通讯录。系统里已有相同号码的会跳过，" +
                    "不会写出重复卡片。卸载前想还原数据就用这一步。"
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 44.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (!canWrite) {
                            permissionLauncher.launch(
                                arrayOf(android.Manifest.permission.WRITE_CONTACTS)
                            )
                        } else {
                            vm.exportToSystem()
                        }
                    },
                    enabled = !vm.busy && contacts.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        when {
                            !canWrite -> "授予写入权限"
                            contacts.isEmpty() -> "本应用还没有联系人"
                            else -> "导出 ${contacts.size} 人到系统"
                        }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            StepCard(
                index = 4,
                title = "清空系统通讯录",
                desc = "按系统当前的全部联系人删除：重复的、没有号码的、导入之后才新增的，一次清干净。" +
                    "本应用内的加密副本会保留，仍然可以查和拨号。",
                warning = true
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 44.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (!canWrite) {
                            permissionLauncher.launch(
                                arrayOf(android.Manifest.permission.WRITE_CONTACTS)
                            )
                        } else {
                            vm.requestWipeAll()
                        }
                    },
                    enabled = !vm.busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        when {
                            !canWrite -> "授予写入权限"
                            !vm.systemScanned -> "正在读取系统通讯录…"
                            vm.systemContactCount == 0 -> "系统通讯录已是空的"
                            else -> "清空系统 ${vm.systemContactCount} 人"
                        }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

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
                        Text("删除前请确认", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "· 若联系人来自 Google / 手机厂商云账号，删除会被同步适配器推送到云端\n" +
                            "· 系统「回收站」通常只保留 30 天，超时后不可恢复\n" +
                            "· 建议先把 CSV 备份存到电脑或 U 盘，确认无误后再清理",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                if (versionName.isBlank()) "数据仅存本机" else "当前版本 v$versionName · 数据仅存本机",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 44.dp)
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StepCard(index: Int, title: String, desc: String, warning: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                color = if (warning) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(32.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = index.toString(),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (warning) Color(0xFFB45309) else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(bottom = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
