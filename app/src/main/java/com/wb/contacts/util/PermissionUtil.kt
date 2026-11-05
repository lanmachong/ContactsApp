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
     * 写 / 删短信。
     *
     * WRITE_SMS 是 signature 级权限，不在公开 SDK 里（编译期没有常量），
     * 普通应用申请不到 —— 真正的删除通路是「成为默认短信应用」，
     * 见 [isDefaultSmsApp] / [defaultSmsAppIntent]。
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

    /** 申请成为默认短信应用 */
    fun defaultSmsAppIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = context.getSystemService(RoleManager::class.java) ?: return null
            if (!manager.isRoleAvailable(RoleManager.ROLE_SMS)) return null
            return manager.createRequestRoleIntent(RoleManager.ROLE_SMS)
        }
        return Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
    }

    /** 读通话记录 */
    fun canReadCallLog(context: Context): Boolean =
        has(context, Manifest.permission.READ_CALL_LOG)

    /**
     * 写 / 删通话记录。
     * WRITE_CALL_LOG 是 signature|privileged 权限，普通应用拿不到，
     * 这里的判断只用于如实告知用户「系统不会让你删」，而不是假装能删。
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
