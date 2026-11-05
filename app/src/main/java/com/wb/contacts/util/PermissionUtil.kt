package com.wb.contacts.util

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import androidx.core.content.ContextCompat

/**
 * 权限与系统设置入口。
 * 四个核心功能各自对应一组权限，这里集中收口，UI 只关心「能不能用」。
 */
object PermissionUtil {

    fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun canReadContacts(context: Context): Boolean = has(context, Manifest.permission.READ_CONTACTS)

    fun canWriteContacts(context: Context): Boolean = has(context, Manifest.permission.WRITE_CONTACTS)

    fun canReadPhoneState(context: Context): Boolean =
        has(context, Manifest.permission.READ_PHONE_STATE)

    /** 读短信 */
    fun canReadSms(context: Context): Boolean = has(context, Manifest.permission.READ_SMS)

    /**
     * 写 / 删短信的**权限**层面。
     *
     * ⚠️ 有这个权限**不等于**能往系统短信库里写东西。
     * Android 4.4 起，只有「默认短信应用」才能读写 SMS Provider；
     * 不是默认应用时，`insert` 不报错、也返回非 null 的 Uri，
     * 但短信根本不会进短信库 —— 典型的静默失败。
     * 所以判断「能不能写短信」必须用 [isDefaultSmsApp]，
     * 这里只能作为「角色之外再确认一下权限」的辅助判据。
     */
    fun canWriteSms(context: Context): Boolean = has(context, PERM_WRITE_SMS)

    /** 是否已经是默认短信应用（有了它才能删短信、写短信） */
    fun isDefaultSmsApp(context: Context): Boolean {
        val expected = context.packageName
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val manager = context.getSystemService(RoleManager::class.java)
                    ?: return false
                manager.isRoleHeld(RoleManager.ROLE_SMS)
            } else {
                Telephony.Sms.getDefaultSmsPackage(context) == expected
            }
        }.getOrDefault(false)
    }

    /**
     * 跳转到「设为默认短信应用」的界面。
     *
     * 返回 null 表示本机找不到任何可用的入口，调用方应如实提示用户手动去系统设置里改。
     *
     * Android 10 起走 RoleManager；9 及以下走 ACTION_CHANGE_DEFAULT；
     * 两者都可能因为机型定制而失效，所以再兜底一层「应用详情页」，
     * 让用户至少能进到系统设置里找到默认短信应用这一项。
     */
    fun defaultSmsAppIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleIntent = runCatching {
                val manager = context.getSystemService(RoleManager::class.java)
                    ?: return@runCatching null
                if (!manager.isRoleAvailable(RoleManager.ROLE_SMS)) return@runCatching null
                manager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            }.getOrNull()
            if (roleIntent != null) return roleIntent
        } else {
            val legacy = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
            if (canResolve(context, legacy)) return legacy
        }

        // 兜底：进应用详情页，用户可在系统里手动切换默认短信应用
        return appDetailsIntent(context)
    }

    /** 应用详情页，任何机型都能打开 */
    fun appDetailsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:${context.packageName}")
        )

    /**
     * 「设为默认短信应用」的最佳跳转目标。
     *
     * Android 10+ 的角色请求对话框要求应用在清单里声明完整的短信组件
     * （短信接收器、WAP Push 接收器、撰写 activity、回复 service），
     * 本应用是通讯录备份工具，没有这些组件，对话框可能直接不出现。
     * 所以这里做了三级兜底：角色请求 → 系统默认应用页 → 应用详情页，
     * 保证任何机型都能把用户带到能改设置的地方。
     */
    fun defaultSmsAppTargetIntent(context: Context): Intent? {
        val roleIntent = defaultSmsAppIntent(context)
        val fallback = defaultAppsSettingsIntent(context)
        return when {
            roleIntent != null && canResolve(context, roleIntent) -> roleIntent
            fallback != null -> fallback
            else -> appDetailsIntent(context)
        }
    }

    /** 系统「默认应用」设置页，Android 7.0+ 都有 */
    fun defaultAppsSettingsIntent(context: Context): Intent? {
        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        return if (canResolve(context, intent)) intent else null
    }

    /** 这个 Intent 在当前设备上有没有应用能接住 */
    fun canResolve(context: Context, intent: Intent): Boolean =
        runCatching {
            context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
        }.getOrDefault(false)

    /** 读通话记录 */
    fun canReadCallLog(context: Context): Boolean =
        has(context, Manifest.permission.READ_CALL_LOG)

    /**
     * 写 / 删通话记录。
     *
     * WRITE_CALL_LOG 是 signature|privileged 权限，普通应用拿不到，
     * 而且清单里声明它也换不来权限，只会在系统的应用权限列表里多一条吓人条目，
     * 所以本应用干脆不声明它。系统会拒绝删除，这里如实告知用户。
     */
    fun canWriteCallLog(context: Context): Boolean =
        has(context, Manifest.permission.WRITE_CALL_LOG)

    private const val PERM_WRITE_SMS = "android.permission.WRITE_SMS"

    fun canNotify(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            has(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

    /** 悬浮窗权限：拨号界面之上弹卡片的前提 */
    fun canDrawOverlays(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true

    fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}")
        )

    /** Android 10+ 才需要「来电显示和骚扰拦截」角色 */
    fun needsScreeningRole(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun hasScreeningRole(context: Context): Boolean {
        if (!needsScreeningRole()) return true
        val manager = context.getSystemService(RoleManager::class.java) ?: return false
        return manager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    fun screeningRoleIntent(context: Context): Intent? {
        if (!needsScreeningRole()) return null
        val manager = context.getSystemService(RoleManager::class.java) ?: return null
        if (!manager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return null
        return manager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
    }
}
