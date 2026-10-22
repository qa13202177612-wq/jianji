package com.jianji.app.screen

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object ScreenRecognitionUtils {

    fun isEnabled(context: Context): Boolean {
        val expected = ComponentName(context, ScreenRecognitionService::class.java)

        val manager = context.getSystemService(AccessibilityManager::class.java)
        val enabled = manager
            ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .orEmpty()
        if (enabled.any { info ->
                val serviceInfo = info.resolveInfo?.serviceInfo
                serviceInfo != null &&
                    serviceInfo.packageName == expected.packageName &&
                    serviceInfo.name == expected.className
            }
        ) {
            return true
        }

        // 部分 ROM 的无障碍列表刷新有延迟，直接读系统设置兜底
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return raw.split(':').any { value ->
            val component = ComponentName.unflattenFromString(value)
            component != null &&
                component.packageName == context.packageName &&
                (component.className == ScreenRecognitionService::class.java.name ||
                    component.className == ".screen.ScreenRecognitionService")
        }
    }

    fun openAccessibilitySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openDemo(context: Context) {
        runCatching {
            context.startActivity(
                Intent(context, ScreenDemoActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
