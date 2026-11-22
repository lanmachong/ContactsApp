package com.wb.contacts.call

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.wb.contacts.R
import com.wb.contacts.data.AvatarCatalog
import com.wb.contacts.data.Contact

/**
 * 来电卡片：只显示动物头像 + 姓名（纯展示，无按钮）。
 *
 * overlay（解锁态）与全屏 Activity（锁屏态）共用这一个构建器，两处长得一样。
 * 纯代码构建 View：来电链路要尽量轻、冷启动快。
 */
object CallCardView {

    /** 构建卡片。animateAvatar 控制头像是否呼吸（全屏来电页里开，悬浮窗里关） */
    fun build(context: Context, contact: Contact?, number: String, animateAvatar: Boolean): View {
        val density = context.resources.displayMetrics.density
        fun px(value: Int) = (value * density).toInt()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = px(22).toFloat()
                setColor(CARD_BG)
                setStroke(px(1), CARD_STROKE)
            }
            elevation = px(12).toFloat()
            setPadding(px(22), px(16), px(22), px(16))
        }

        // 动物头像（圆形底 + 呼吸动画）
        val avatar = ImageView(context).apply {
            val idx = if (contact != null) AvatarCatalog.resolve(contact) else FALLBACK_AVATAR
            setImageResource(AvatarCatalog.drawableRes(idx))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = context.getString(AvatarCatalog.nameRes(idx))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(AVATAR_BG)
            }
            layoutParams = LinearLayout.LayoutParams(px(60), px(60))
        }
        if (animateAvatar) startBreathing(avatar)
        card.addView(avatar)

        // 姓名：未识别到联系人时显示"未知号码"（号码本身交给系统来电界面显示）
        val name = TextView(context).apply {
            text = contact?.name?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.overlay_unknown_number)
            setTextColor(Color.WHITE)
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(px(16), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        card.addView(name)
        return card
    }

    /** 头像轻微呼吸：scale 1.0 → 1.06 → 1.0，循环 */
    private fun startBreathing(view: View) {
        val up = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.06f).apply { duration = 900 }
        val upY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.06f).apply { duration = 900 }
        val down = ObjectAnimator.ofFloat(view, View.SCALE_X, 1.06f, 1f).apply { duration = 900 }
        val downY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1.06f, 1f).apply { duration = 900 }
        AnimatorSet().apply {
            play(up).with(upY)
            play(down).with(downY).after(up).after(upY)
            startDelay = 200
            addListener(object : android.animation.AnimatorListenerAdapter() {
                var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    cancelled = true
                }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!cancelled) start()
                }
            })
            start()
        }
    }

    private const val CARD_BG = 0xF21E222B.toInt()
    private const val CARD_STROKE = 0x33FFFFFF
    private const val AVATAR_BG = 0x22FFFFFF
    private const val FALLBACK_AVATAR = 0
}
