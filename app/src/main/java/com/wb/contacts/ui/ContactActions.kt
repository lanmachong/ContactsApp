package com.wb.contacts.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.wb.contacts.R

/** 与系统应用交互：拨号盘、短信、剪贴板。全部通过隐式 Intent，无需任何权限。 */
object ContactActions {

    private fun normalized(raw: String): String =
        raw.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

    fun dial(context: Context, raw: String) {
        val number = normalized(raw)
        if (number.isEmpty()) {
            toast(context, context.getString(R.string.toast_number_empty))
            return
        }
        start(
            context,
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")),
            context.getString(R.string.toast_no_dial_app)
        )
    }

    fun sms(context: Context, raw: String) {
        val number = normalized(raw)
        if (number.isEmpty()) {
            toast(context, context.getString(R.string.toast_number_empty))
            return
        }
        start(
            context,
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")),
            context.getString(R.string.toast_no_sms_app)
        )
    }

    fun copy(context: Context, raw: String) {
        if (raw.isBlank()) {
            toast(context, context.getString(R.string.toast_number_empty))
            return
        }
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (manager == null) {
            toast(context, context.getString(R.string.toast_copy_failed))
            return
        }
        manager.setPrimaryClip(ClipData.newPlainText("phone_number", raw))
        toast(context, context.getString(R.string.toast_copied))
    }

    private fun start(context: Context, base: Intent, failure: String) {
        try {
            base.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(base)
        } catch (e: Exception) {
            toast(context, failure)
        }
    }

    private fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
