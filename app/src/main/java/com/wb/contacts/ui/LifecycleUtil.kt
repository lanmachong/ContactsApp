package com.wb.contacts.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * 页面回到前台时执行（用于刷新「权限是否已授予」这类系统状态）。
 * 从设置页授权返回后，页面必须重新判断一次，否则按钮仍显示未授权。
 */
@Composable
fun OnResume(onResume: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val current by rememberUpdatedState(onResume)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) current()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
