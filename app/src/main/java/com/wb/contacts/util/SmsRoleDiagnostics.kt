package com.wb.contacts.util

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * 「为什么系统默认短信应用列表里没有本应用」的自检。
 *
 * AOSP 的 `SmsApplication.getApplicationCollection()` 是系统筛候选的地方，
 * 它按四条固定的 Intent 去查四个组件，`SmsApplicationData.isComplete()`
 * 要求四项全部命中，应用才会出现在「默认短信应用」列表里：
 *
 * · `SMS_DELIVER` 广播接收器，且声明 `BROADCAST_SMS` 权限
 * · `WAP_PUSH_DELIVER` 广播接收器，按 `setDataAndType(null, 彩信 mimeType)` 查询，
 *   且声明 `BROADCAST_WAP_PUSH` 权限 ← 少这个 mimeType 就查不到
 * · `RESPOND_VIA_MESSAGE` 服务，按 `smsto:` 查询，且声明 `SEND_RESPOND_VIA_MESSAGE` 权限
 * · `ACTION_SENDTO` 界面，按 `smsto:` 查询
 *
 * 这里用同样的 Intent 复查一遍自己，把结果摆到界面上：
 * 缺哪项就补哪项；四项齐全却仍不显示，那基本就是厂商 ROM 的白名单限制，
 * 不该再让用户对着一个空白列表猜。
 */
object SmsRoleDiagnostics {

    private const val SMS_DELIVER = "android.provider.Telephony.SMS_DELIVER"
    private const val WAP_PUSH_DELIVER = "android.provider.Telephony.WAP_PUSH_DELIVER"
    private const val RESPOND_VIA_MESSAGE = "android.intent.action.RESPOND_VIA_MESSAGE"
    private const val MMS_MIME = "application/vnd.wap.mms-message"

    private const val PERM_BROADCAST_SMS = "android.permission.BROADCAST_SMS"
    private const val PERM_BROADCAST_WAP_PUSH = "android.permission.BROADCAST_WAP_PUSH"
    private const val PERM_SEND_RESPOND_VIA_MESSAGE =
        "android.permission.SEND_RESPOND_VIA_MESSAGE"

    data class Report(
        /** 四项组件声明是否全部被系统查到 */
        val smsReceiver: Boolean,
        val mmsReceiver: Boolean,
        val respondService: Boolean,
        val sendToActivity: Boolean,
        /** 本机是否提供 ROLE_SMS（无通话功能的设备 / 定制 ROM 可能没有） */
        val roleAvailable: Boolean,
        /** 当前是否已持有默认短信应用身份 */
        val isDefault: Boolean,
        /** WRITE_SMS 的授权状态，仅作参考，不作为拦截条件 */
        val writeSmsGranted: Boolean
    ) {
        val qualified: Boolean
            get() = smsReceiver && mmsReceiver && respondService && sendToActivity
    }

    fun check(context: Context): Report {
        val pm = context.packageManager
        val pkg = context.packageName

        // 1. 短信接收器：SMS_DELIVER + BROADCAST_SMS
        val smsReceiver = runCatching {
            pm.queryBroadcastReceivers(Intent(SMS_DELIVER), 0).any { r ->
                val info = r.activityInfo
                info != null && info.packageName == pkg &&
                    info.permission == PERM_BROADCAST_SMS
            }
        }.getOrDefault(false)

        // 2. 彩信接收器：必须带 mimeType，和系统查询方式一致
        val mmsReceiver = runCatching {
            val intent = Intent(WAP_PUSH_DELIVER).apply { type = MMS_MIME }
            pm.queryBroadcastReceivers(intent, 0).any { r ->
                val info = r.activityInfo
                info != null && info.packageName == pkg &&
                    info.permission == PERM_BROADCAST_WAP_PUSH
            }
        }.getOrDefault(false)

        // 3. 快速回复服务：系统按 smsto: 查
        val respondService = runCatching {
            val intent = Intent(RESPOND_VIA_MESSAGE, Uri.fromParts("smsto", "", null))
            pm.queryIntentServices(intent, 0).any { r ->
                val info = r.serviceInfo
                info != null && info.packageName == pkg &&
                    info.permission == PERM_SEND_RESPOND_VIA_MESSAGE
            }
        }.getOrDefault(false)

        // 4. 撰写界面：系统同样按 smsto: 查
        val sendToActivity = runCatching {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", "", null))
            pm.queryIntentActivities(intent, 0).any { r ->
                r.activityInfo?.packageName == pkg
            }
        }.getOrDefault(false)

        val roleAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.getSystemService(RoleManager::class.java)
                    ?.isRoleAvailable(RoleManager.ROLE_SMS) ?: false
            }.getOrDefault(false)
        } else {
            // Android 9 及以下没有角色概念，走 ACTION_CHANGE_DEFAULT，视为可用
            true
        }

        return Report(
            smsReceiver = smsReceiver,
            mmsReceiver = mmsReceiver,
            respondService = respondService,
            sendToActivity = sendToActivity,
            roleAvailable = roleAvailable,
            isDefault = PermissionUtil.isDefaultSmsApp(context),
            writeSmsGranted = PermissionUtil.canWriteSms(context)
        )
    }
}
