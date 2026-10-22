package com.jianji.app.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jianji.app.data.AppSettings
import com.jianji.app.ui.components.ScreenHeader
import com.jianji.app.ui.components.ScreenRecognitionCard
import com.jianji.app.ui.components.SectionCard
import com.jianji.app.ui.components.rememberSmsPermissionUi
import com.jianji.app.util.CsvExporter
import com.jianji.app.viewmodel.BillViewModel
import com.jianji.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun ProfileScreen(
    settingsVm: SettingsViewModel,
    billVm: BillViewModel,
    settings: AppSettings,
    onOpenBudget: () -> Unit
) {
    val context = LocalContext.current
    val bills by billVm.allBills.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val smsPermission = rememberSmsPermissionUi()
    var exporting by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        exporting = true
        scope.launch {
            try {
                val snapshot = billVm.snapshot()
                val result = CsvExporter.export(context, uri, snapshot)
                val message = result.fold(
                    onSuccess = { "已导出 $it 笔账单" },
                    onFailure = { "导出失败：${it.message ?: "未知错误"}" }
                )
                snackbarHostState.showSnackbar(message)
            } catch (t: Throwable) {
                snackbarHostState.showSnackbar("导出失败：${t.message ?: "未知错误"}")
            } finally {
                exporting = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            ScreenHeader(title = "我的", subtitle = "简单记录，认真生活")

            Spacer(Modifier.height(16.dp))

            // 概览
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🐳", fontSize = 28.sp)
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "简记",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "本地记账 · 已记录 ${bills.size} 笔",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 外观
            SectionCard {
                Text(
                    text = "外观",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChip(
                        label = "跟随系统",
                        selected = settings.themeMode == AppSettings.THEME_SYSTEM,
                        onClick = { settingsVm.setThemeMode(AppSettings.THEME_SYSTEM) }
                    )
                    ThemeChip(
                        label = "浅色",
                        selected = settings.themeMode == AppSettings.THEME_LIGHT,
                        onClick = { settingsVm.setThemeMode(AppSettings.THEME_LIGHT) }
                    )
                    ThemeChip(
                        label = "深色",
                        selected = settings.themeMode == AppSettings.THEME_DARK,
                        onClick = { settingsVm.setThemeMode(AppSettings.THEME_DARK) }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 自动记账
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "短信自动记账",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (smsPermission.granted) {
                                "已授权，银行/支付短信会自动生成账单"
                            } else {
                                "未授权短信权限，暂时只能手动记账"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (smsPermission.granted) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    Switch(
                        checked = settings.autoSms,
                        onCheckedChange = { enabled ->
                            settingsVm.setAutoSms(enabled)
                            if (enabled && !smsPermission.granted) {
                                smsPermission.request()
                            }
                        }
                    )
                }

                if (!smsPermission.granted) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { smsPermission.request() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("去授权")
                        }
                        OutlinedButton(
                            onClick = { smsPermission.openSettings() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("系统设置")
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 屏幕识别
            ScreenRecognitionCard()

            Spacer(Modifier.height(14.dp))

            // 数据
            SectionCard {
                Text(
                    text = "数据",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "账单保存在本机 Room 数据库，卸载应用会一并删除",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (!exporting) {
                                exportLauncher.launch("简记账单_${LocalDate.now()}.csv")
                            }
                        },
                        enabled = !exporting,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(if (exporting) "导出中…" else "导出 CSV")
                    }
                    OutlinedButton(
                        onClick = onOpenBudget,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("月度预算")
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 关于
            SectionCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                Text(
                    text = "关于简记",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "版本 1.0.0\n" +
                        "技术栈：Kotlin · Jetpack Compose · Material 3 · Room · DataStore\n" +
                        "最低支持 Android 10，适配 Android 14\n\n" +
                        "短信内容只用于本机解析，不会上传到任何服务器。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
private fun ThemeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}
