package com.wb.contacts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wb.contacts.R
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * 耗时任务的全局阻塞进度层。
 *
 * 【为什么用 Dialog 而不是普通 Box】
 * 以前只在 HomeScreen 里画了个小转圈，而备份 / 还原 / 清空页 busy 时
 * 界面上只有一个变灰的按钮 —— 数据一多就像「点了没反应」。
 * 这里改成独立窗口的全屏模态层：
 * · 覆盖所有页面（含顶部菜单、悬浮按钮、结果弹窗）
 * · 返回键、点击外部都吃掉，任务完成前点不到软件其他地方
 * · 显示当前阶段、已处理条数、预计剩余倒计时、已用时间
 *
 * 分母是所有待处理数据的真实条数，逐条 / 逐批上报，不再按「勾了几项」算。
 * 倒计时优先按最近 8 秒的实测速率外推，其次用全程平均速率，
 * 都拿不到时才用调用方给的粗估时长兜底，并明确标注是估算值。
 * 系统 Home 键和「强制停止」是应用拦不住的，所以提示里会写明别这么做。
 */
@Composable
fun BusyOverlay(vm: ContactsViewModel) {
    // 每秒刷新一次，倒计时才会真的走起来；同时采一份进度样本
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var samples by remember { mutableStateOf(listOf<Pair<Long, Int>>()) }
    LaunchedEffect(vm.busyStartedAt) {
        samples = emptyList()
        while (true) {
            delay(1000L)
            val tick = System.currentTimeMillis()
            now = tick
            samples = (samples + (tick to vm.busyDone)).takeLast(9)
        }
    }

    val elapsedMs = (now - vm.busyStartedAt).coerceAtLeast(0L)
    val elapsedSec = elapsedMs / 1000L
    val done = vm.busyDone
    val total = vm.busyTotal
    val fraction = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else -1f

    // 用最近 8 秒的实测速率外推。全局平均速率会被「开头的整理阶段很快、
    // 后面的加密写入很慢」拉偏，倒计时会越估越短；滑动窗口更贴近当前阶段。
    val ratePerSec: Double? = if (samples.size >= 3) {
        val (t0, d0) = samples.first()
        val (t1, d1) = samples.last()
        val window = (t1 - t0) / 1000.0
        if (window >= 2.0 && d1 > d0) (d1 - d0) / window else null
    } else {
        null
    }

    // 有真实进度就按实测速率外推；否则退回粗估时长，用完后如实说「仍在处理」
    val etaSec: Long? = when {
        total > 0 && done >= total -> 0L
        ratePerSec != null && total > done ->
            ((total - done) / ratePerSec).toLong().coerceAtLeast(1L)
        done > 0 && total > done ->
            (elapsedMs * (total - done) / done / 1000L).coerceAtLeast(1L)
        vm.busyEstimateSeconds > 0 ->
            (vm.busyEstimateSeconds.toLong() - elapsedSec).coerceAtLeast(0L)
        else -> null
    }
    val finished = total > 0 && done >= total
    val overdue = etaSec == 0L && !finished
    val etaText = when {
        finished -> stringResource(R.string.busy_overlay_eta_done)
        overdue -> stringResource(R.string.busy_overlay_eta_overdue)
        etaSec != null -> stringResource(R.string.busy_overlay_eta, formatClock(etaSec))
        else -> stringResource(R.string.busy_overlay_estimating)
    }

    Dialog(
        onDismissRequest = { /* 任务完成前不允许关闭 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                // 吃掉遮罩上的所有触摸，绝不透传给下层界面
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { },
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 进度圈：拿得到总量就显示百分比，拿不到就转 indeterminate
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(88.dp)) {
                        if (fraction >= 0f) {
                            CircularProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 7.dp
                            )
                            Text(
                                "${(fraction * 100).toInt()}%",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(64.dp),
                                strokeWidth = 6.dp
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Text(
                        vm.busyText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    if (vm.busyPhase.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            vm.busyPhase,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (fraction >= 0f) {
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.busy_overlay_progress, done, total),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 实时速率：数字一直在动，也能看出倒计时是按什么算出来的
                    if (ratePerSec != null && !finished) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(
                                R.string.busy_overlay_rate,
                                ratePerSec.toInt().coerceAtLeast(1)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.busy_overlay_elapsed, formatClock(elapsedSec)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            etaText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.busy_overlay_eta_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(18.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.busy_overlay_no_close),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

/** mm:ss，超过一小时自动变成 h:mm:ss */
private fun formatClock(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, sec)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", m, sec)
    }
}
