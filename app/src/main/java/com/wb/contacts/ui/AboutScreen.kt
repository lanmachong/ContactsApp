package com.wb.contacts.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 关于页里的静态信息，改这里就能改全局 */
object AppInfo {
    const val APP_NAME = "本地加密通讯录"

    const val DEVELOPER = "懒码虫"

    const val GITHUB_URL = "https://github.com/lanmachong"

    const val GITHUB_REPO = "https://github.com/lanmachong/ContactsApp"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(vm: ContactsViewModel) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.applicationContext.packageManager
                .getPackageInfo(context.applicationContext.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
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
            Spacer(Modifier.height(12.dp))

            Text(
                AppInfo.APP_NAME,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (versionName.isBlank()) "数据全部留在本机" else "v$versionName · 数据全部留在本机",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            Text(
                "它解决什么问题",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "把手机通讯录搬进一个加密的本地库：整条记录用 AES-256-GCM 加密后落盘，" +
                    "数据库里没有明文的姓名和号码；再配合来电识别悬浮窗，陌生号码也能看出是谁。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            Text(
                "核心功能",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))

            FeatureRow(
                icon = Icons.Default.SyncAlt,
                title = "导入 / 导出",
                desc = "一次性把系统通讯录搬进来；清空系统后仍可随时导回系统，卸载也不怕"
            )
            FeatureRow(
                icon = Icons.Default.Lock,
                title = "加密存储",
                desc = "密钥由系统 Keystore 生成，永不落盘、不可导出；数据库被拷走也读不出内容"
            )
            FeatureRow(
                icon = Icons.Default.Chat,
                title = "短信备份",
                desc = "短信内容整体加密入库，可查看、可导出 CSV，也可清空系统里的原短信"
            )
            FeatureRow(
                icon = Icons.Default.Call,
                title = "通话记录备份",
                desc = "通话记录加密留存，号码与时长不落明文，随时能查能导出"
            )
            FeatureRow(
                icon = Icons.Default.Phone,
                title = "来电识别",
                desc = "来电时弹悬浮窗显示对方姓名，只识别不拦截、不录音、不上传"
            )
            FeatureRow(
                icon = Icons.Default.CloudOff,
                title = "纯本地",
                desc = "没有账号、没有服务器、没有埋点，所有数据只在这台手机上"
            )

            Spacer(Modifier.height(20.dp))

            Text(
                "权限说明",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "每个权限都是对应功能必需，且在你点进那个功能时才申请，不会在启动时一次性索要：\n\n" +
                    "· 通讯录（读）—— 把系统通讯录导入加密库\n" +
                    "· 通讯录（写）—— 把加密库导回系统、清空系统通讯录时才需要\n" +
                    "· 短信（读）—— 把系统短信导入加密库\n" +
                    "· 短信（写）—— 系统规定只有「默认短信应用」才能删短信，" +
                    "所以点清空时若未设置，会直接带你到系统设置页\n" +
                    "· 通话记录（读）—— 把系统通话记录导入加密库\n" +
                    "· 通话状态 —— 挂断后自动撤掉来电悬浮窗\n" +
                    "· 悬浮窗 / 通知 —— 显示来电识别卡片与常驻通知\n\n" +
                    "没有网络权限，没有位置、相机、麦克风、存储权限。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            Text(
                "开发者",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(AppInfo.GITHUB_URL))
                            )
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            AppInfo.DEVELOPER,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            AppInfo.GITHUB_URL,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = "打开 GitHub",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "开源",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(AppInfo.GITHUB_REPO))
                            )
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "本应用已开源",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            AppInfo.GITHUB_REPO,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = "打开源码仓库",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "MIT 许可协议，欢迎自行编译、修改、分发。源码里不含任何后端服务，" +
                    "所有功能都在你的手机上跑。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            Text(
                "使用提示",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "· 重复联系人会被自动打上「重复」标签，搜号码即可定位\n" +
                    "· 卸载 App 会连同加密密钥一起清除，卸载前请先「导出到系统通讯录」或导出 CSV\n" +
                    "· 短信与通话记录同样需要授权才能读取，数据只在本机加密保存\n" +
                    "· 来电悬浮窗需要「显示在其他应用上层」权限，部分国产 ROM 还要额外允许后台弹出界面",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(9.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
