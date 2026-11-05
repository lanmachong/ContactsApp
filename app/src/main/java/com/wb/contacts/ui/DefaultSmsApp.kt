package com.wb.contacts.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wb.contacts.R
import com.wb.contacts.util.PermissionUtil

/**
 * 「设为默认短信应用」的跳转入口。
 *
 * 短信备份页和清空数据页都要用到，抽成公共组件。
 *
 * Android 10+ 的角色请求要求应用在清单里声明完整的短信组件，而且细节要和系统
 * 查询条件完全一致（彩信接收器的 mimeType、回复服务与撰写界面的 smsto scheme）。
 * v1.8.1 已补齐，但部分厂商 ROM 仍会改写或屏蔽角色请求入口，
 * 所以保留三级兜底：角色请求 → 系统默认应用页 → 应用详情页。
 *
 * @param onReturn 用户从系统设置返回后调用，用来复查角色状态
 * @param onFail 连兜底都打不开时调用，提示用户手动去系统设置里改
 */
@Composable
fun rememberDefaultSmsAppLauncher(
    onReturn: () -> Unit = {},
    onFail: () -> Unit = {}
): () -> Unit {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onReturn()
    }

    return remember(context, launcher) {
        {
            val roleIntent = PermissionUtil.defaultSmsAppIntent(context)
            val fallback = PermissionUtil.defaultAppsSettingsIntent(context)

            val target = when {
                roleIntent != null && PermissionUtil.canResolve(context, roleIntent) -> roleIntent
                fallback != null -> fallback
                else -> PermissionUtil.appDetailsIntent(context)
            }

            runCatching { launcher.launch(target) }
                .onFailure {
                    // 连兜底都启动不了，就直接把应用详情页给出去
                    runCatching { launcher.launch(PermissionUtil.appDetailsIntent(context)) }
                        .onFailure { onFail() }
                }
        }
    }
}

/**
 * 「切回原来的默认短信应用」的跳转入口。
 *
 * Android 没有让应用主动交还角色的 API：createRequestRoleIntent() 只能请求「成为」
 * 默认应用，角色要等用户在系统设置里改选别的应用才会转移。
 * 所以这里只负责把用户带到能改设置的地方，不会、也不能替用户自动切回。
 */
@Composable
fun rememberSwitchBackSmsLauncher(
    onReturn: () -> Unit = {},
    onFail: () -> Unit = {}
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onReturn() }

    return remember(context, launcher) {
        {
            runCatching { launcher.launch(PermissionUtil.switchBackDefaultSmsIntent(context)) }
                .onFailure { onFail() }
        }
    }
}

/**
 * 权限 / 身份预检提示卡，清空页和还原页共用。
 * 条件没到位时提前说清楚并给出跳转按钮，别让用户执行完才知道被系统拒绝。
 */
@Composable
fun ActionNotice(
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
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Text(actionText)
            }
        }
    }
}

/**
 * 「本应用当前正占着默认短信应用角色」的常驻提醒。
 *
 * 这个角色**不会**过几分钟自动还给原来的短信应用，系统也没有自动交还机制。
 * 期间收到的文字短信会由 SmsCompatReceiver 写回系统短信库（内容不丢），
 * 但本应用不发新短信通知、彩信也不处理，所以操作完必须手动切回。
 */
@Composable
fun SmsRoleActiveNotice(onSwitchBack: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.sms_role_active_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.sms_role_active_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onSwitchBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sms_role_switch_back_action))
            }
        }
    }
}
