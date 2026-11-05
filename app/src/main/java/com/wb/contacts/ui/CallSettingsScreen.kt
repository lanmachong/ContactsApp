package com.wb.contacts.ui

import android.os.Build
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.wb.contacts.call.CallIdService
import com.wb.contacts.call.CallOverlayController
import com.wb.contacts.call.CallerIndex
import com.wb.contacts.util.PermissionUtil
import com.wb.contacts.util.Prefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallSettingsScreen(vm: ContactsViewModel) {
    val context = LocalContext.current
    val contacts by vm.contacts.collectAsState()
    var tick by remember { mutableStateOf(0) }
    var enabled by remember { mutableStateOf(Prefs.isCallIdEnabled(context)) }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { tick++ }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { tick++ }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { tick++ }

    val phoneStateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { tick++ }

    OnResume {
        tick++
        vm.refreshCallState()
        enabled = Prefs.isCallIdEnabled(context)
    }

    val canOverlay = remember(tick) { PermissionUtil.canDrawOverlays(context) }
    val hasRole = remember(tick) { PermissionUtil.hasScreeningRole(context) }
    val canNotify = remember(tick) { PermissionUtil.canNotify(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("来电识别") },
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

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "来电时在拨号界面顶部显示姓名",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "号码匹配在手机本地完成，姓名和号码不会发给任何服务器。" +
                            "已建立 $callerIndexCount 个号码的索引。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "当前可识别 ${contacts.size} 位联系人的号码",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            ToggleRow(
                title = "开启来电识别",
                subtitle = if (enabled) "前台服务运行中，来电时会自动弹出悬浮窗"
                else "开启后会常驻一个低优先级通知，保证进程不被系统回收",
                checked = enabled,
                onCheckedChange = { on ->
                    if (on && !canOverlay) {
                        overlayLauncher.launch(PermissionUtil.overlaySettingsIntent(context))
                        return@ToggleRow
                    }
                    enabled = on
                    Prefs.setCallIdEnabled(context, on)
                    if (on) {
                        CallIdService.start(context)
                        vm.onCallServiceChanged(true)
                    } else {
                        CallIdService.stop(context)
                        vm.onCallServiceChanged(false)
                    }
                }
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(8.dp))

            PermissionRow(
                title = "显示在其他应用上层",
                subtitle = "悬浮窗必须的权限",
                granted = canOverlay,
                onRequest = { overlayLauncher.launch(PermissionUtil.overlaySettingsIntent(context)) }
            )

            if (PermissionUtil.needsScreeningRole()) {
                PermissionRow(
                    title = "来电显示和骚扰拦截",
                    subtitle = if (hasRole) "已授权，可在振铃瞬间拿到号码"
                    else "Android 10 起需要授予该角色才能读取来电号码",
                    granted = hasRole,
                    onRequest = {
                        val intent = PermissionUtil.screeningRoleIntent(context)
                        if (intent != null) {
                            roleLauncher.launch(intent)
                        } else {
                            vm.onCallServiceChanged(true)
                        }
                    }
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRow(
                    title = "通知权限",
                    subtitle = "用于显示「来电识别已开启」的常驻通知",
                    granted = canNotify,
                    onRequest = { notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                )
            }

            PermissionRow(
                title = "通话状态权限",
                subtitle = "挂断后自动撤掉悬浮窗",
                granted = PermissionUtil.canReadPhoneState(context),
                onRequest = {
                    phoneStateLauncher.launch(android.Manifest.permission.READ_PHONE_STATE)
                }
            )

            Spacer(Modifier.height(20.dp))

            OutlinedButton(
                onClick = {
                    if (!PermissionUtil.canDrawOverlays(context)) {
                        overlayLauncher.launch(PermissionUtil.overlaySettingsIntent(context))
                        return@OutlinedButton
                    }
                    // 不用真的打电话也能验证悬浮窗效果
                    val sample = contacts.firstOrNull()
                    CallOverlayController.show(
                        context,
                        sample?.phone ?: "13800138000",
                        sample
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("预览悬浮窗效果") }

            Spacer(Modifier.height(16.dp))
            Text(
                "说明：本应用只读取号码做匹配，不录音、不上传、不拦截来电。" +
                    "关闭开关后前台服务和悬浮窗都会停止。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

private val callerIndexCount: Int
    get() = CallerIndex.size

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (granted) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint = if (granted) Color(0xFF16A34A) else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!granted) {
            TextButton(onClick = onRequest) { Text("去授权") }
        }
    }
}
