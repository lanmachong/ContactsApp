package com.wb.contacts.call

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.wb.contacts.MainActivity
import com.wb.contacts.data.Contact

/**
 * 来电悬浮窗。
 * 使用 WindowManager + TYPE_APPLICATION_OVERLAY，需要「显示在其他应用上层」权限。
 * 卡片不可获得焦点，避免抢走系统来电界面的按键与触控。
 */
class CallOverlay(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var rootView: View? = null
    private var hideRunnable: Runnable? = null

    fun canDraw(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true

    fun show(contact: Contact?, number: String) {
        if (!canDraw()) return
        hide()

        val view = buildCard(contact, number)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(48)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val added = runCatching { windowManager.addView(view, params) }.isSuccess
        if (!added) return
        rootView = view

        val timeout = Runnable { hide() }
        hideRunnable = timeout
        handler.postDelayed(timeout, HIDE_DELAY_MS)
    }

    fun hide() {
        hideRunnable?.let { handler.removeCallbacks(it) }
        hideRunnable = null
        val view = rootView ?: return
        rootView = null
        runCatching { windowManager.removeView(view) }
    }

    private fun buildCard(contact: Contact?, number: String): View {
        val density = context.resources.displayMetrics.density
        fun px(value: Int) = (value * density).toInt()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = px(20).toFloat()
                setColor(CARD_BG)
                setStroke(px(1), CARD_STROKE)
            }
            elevation = px(10).toFloat()
            setPadding(px(18), px(16), px(18), px(12))
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val initial = (contact?.name?.trim()?.takeIf { it.isNotEmpty() } ?: "?").let { name ->
            name.firstOrNull()?.uppercase() ?: "#"
        }
        val avatar = TextView(context).apply {
            text = initial
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(paletteColor(contact?.name ?: number))
            }
            layoutParams = LinearLayout.LayoutParams(px(46), px(46))
        }
        row.addView(avatar)

        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(14), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val title = TextView(context).apply {
            text = contact?.name?.takeIf { it.isNotBlank() } ?: "未知号码"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        texts.addView(title)

        val subtitle = TextView(context).apply {
            text = number.ifBlank { "来电号码未知" }
            setTextColor(SUBTITLE)
            textSize = 14f
            maxLines = 1
        }
        texts.addView(subtitle)

        val meta = listOfNotNull(
            contact?.groupName?.takeIf { it.isNotBlank() },
            contact?.tags?.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
            .ifBlank { contact?.note?.takeIf { it.isNotBlank() }?.let { "备注：$it" } ?: "" }

        if (meta.isNotEmpty()) {
            val metaView = TextView(context).apply {
                text = meta
                setTextColor(ACCENT)
                textSize = 12f
                maxLines = 1
                setPadding(0, px(4), 0, 0)
            }
            texts.addView(metaView)
        }

        row.addView(texts)
        card.addView(row)

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, px(10), 0, 0)
        }

        if (contact != null) {
            actions.addView(
                actionButton("查看") {
                    hide()
                    openApp(contact.id)
                }
            )
        }
        actions.addView(actionButton("关闭") { hide() })
        card.addView(actions)

        // 点卡片空白处也能关掉，避免挡住来电界面又关不掉
        card.setOnClickListener { hide() }
        return card
    }

    private fun actionButton(label: String, onClick: () -> Unit): Button =
        Button(context).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            setTextColor(ACCENT)
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(BUTTON_BG)
            }
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(16), dp(6), dp(16), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(8) }
            setOnClickListener { onClick() }
        }

    private fun openApp(contactId: Long) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_OPEN_CONTACT_ID, contactId)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun paletteColor(seed: String): Int =
        PALETTE[(seed.hashCode() and Int.MAX_VALUE) % PALETTE.size]

    companion object {
        private const val HIDE_DELAY_MS = 30_000L
        private const val CARD_BG = 0xF21E222B.toInt()
        private const val CARD_STROKE = 0x33FFFFFF
        private const val SUBTITLE = 0xFFB4BCCC.toInt()
        private const val ACCENT = 0xFF9DBBFF.toInt()
        private const val BUTTON_BG = 0x33FFFFFF
        private val PALETTE = intArrayOf(
            0xFF2563EB.toInt(), 0xFF0D9488.toInt(), 0xFF7C3AED.toInt(),
            0xFFDB2777.toInt(), 0xFFEA580C.toInt(), 0xFF0891B2.toInt()
        )
    }
}
