package com.jianji.app.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

data class SmsPermissionUi(
    val granted: Boolean,
    val request: () -> Unit,
    val openSettings: () -> Unit
)

object SmsPermissionUtils {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

    fun openAppSettings(context: Context) {
        runCatching {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

/**
 * 短信权限状态 + 申请入口。
 * 先展示应用内说明弹窗，再触发系统权限框，符合「权限提示」的合规要求；
 * 被拒绝后可从弹窗或页面按钮直接跳转系统设置。
 */
@Composable
fun rememberSmsPermissionUi(): SmsPermissionUi {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(SmsPermissionUtils.hasPermission(context)) }
    var showDialog by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = SmsPermissionUtils.hasPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val openSettings: () -> Unit = { SmsPermissionUtils.openAppSettings(context) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("开启短信自动记账") },
            text = {
                Text(
                    "简记只读取银行与支付类短信中的金额、收支方向，用于自动生成账单。\n\n" +
                        "· 短信内容仅保存在本机数据库，不会上传\n" +
                        "· 可随时在「我的」中关闭自动记账\n" +
                        "· 若系统权限被永久拒绝，请到系统设置中手动开启"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        launcher.launch(Manifest.permission.RECEIVE_SMS)
                    }
                ) {
                    Text("去授权")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        openSettings()
                    }
                ) {
                    Text("去设置")
                }
            }
        )
    }

    return SmsPermissionUi(
        granted = granted,
        request = { if (!granted) showDialog = true },
        openSettings = openSettings
    )
}
