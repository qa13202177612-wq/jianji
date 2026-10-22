package com.jianji.app.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jianji.app.screen.MaaSConfig
import com.jianji.app.screen.ScreenRecognitionUtils

@Composable
fun ScreenRecognitionCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(ScreenRecognitionUtils.isEnabled(context)) }
    val maasConfigured = remember { MaaSConfig.load(context) != null }

    val notificationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* 拒绝也不影响记账，只是没有通知提醒 */ }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = ScreenRecognitionUtils.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openSettings() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        ScreenRecognitionUtils.openAccessibilitySettings(context)
    }

    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "屏幕识别自动记账",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (enabled) {
                        "已开启 · 识别到支付成功页会自动记账"
                    } else {
                        "未开启 · 需要无障碍权限"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "优先读取页面文字；读不到时截取当前屏幕在本地做 OCR。截图只在内存中处理，不上传、不保存，也不代替你点击或输入。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (maasConfigured) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "调试版已启用千问兜底：本地识别失败时会把页面文字发送到 AI 网关用于识别；截图仍不会上传。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { openSettings() },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (enabled) "管理权限" else "去开启")
            }
            OutlinedButton(
                onClick = { ScreenRecognitionUtils.openDemo(context) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("测试识别")
            }
        }
    }
}
