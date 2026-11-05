package com.wb.contacts.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.wb.contacts.util.PermissionUtil

/**
 * 「设为默认短信应用」的跳转入口。
 *
 * 短信备份页和清空数据页都要用到，抽成公共组件。
 *
 * 这里的坑：Android 10+ 的角色请求要求应用在清单里声明完整的短信组件
 * （短信接收器、WAP Push 接收器、撰写 activity、回复 service），
 * 本应用是通讯录备份工具，没有这些组件，角色请求对话框可能直接不出现。
 * 所以这里做了三级兜底：角色请求 → 系统默认应用页 → 应用详情页，
 * 保证任何机型都能把用户带到能改设置的地方。
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
 * 系统短信的删除权限判断。
 *
 * Android 4.4 起只有「默认短信应用」能删短信。本应用不是默认短信应用时，
 * 系统会直接拒绝删除请求 —— 这时候要给用户一条明确的路，而不是只说「失败了」。
 */
object SmsDeletePath {

    /**
     * 当前能不能删系统短信。
     *
     * 有 WRITE_SMS 就说明系统已经把「默认短信应用」身份给了本应用。
     */
    fun canDelete(context: android.content.Context): Boolean =
        PermissionUtil.canWriteSms(context)
}
