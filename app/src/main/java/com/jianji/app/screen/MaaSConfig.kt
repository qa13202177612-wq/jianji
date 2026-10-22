package com.jianji.app.screen

import android.content.Context
import java.io.File
import java.util.Properties

/**
 * 千问/MaaS 兜底配置。
 *
 * Key 不进入 APK：通过 adb 推送到 App 私有目录的 files/maas.properties。
 * 文件格式：
 *   MAAS_API_KEY=...
 *   MAAS_BASE_URL=https://maas.qianwenaiapi.com/apps/anthropic
 *   MAAS_MODEL=deepseek-v4.1-flash
 *   MAAS_ENABLED=true
 */
object MaaSConfig {

    private const val FILE_NAME = "maas.properties"

    data class Config(
        val apiKey: String,
        val baseUrl: String,
        val model: String
    )

    fun load(context: Context): Config? {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return null

            val props = Properties()
            file.inputStream().use { props.load(it) }

            val apiKey = props.getProperty("MAAS_API_KEY").orEmpty().trim()
            val baseUrl = props.getProperty("MAAS_BASE_URL").orEmpty().trim().trimEnd('/')
            val model = props.getProperty("MAAS_MODEL").orEmpty().trim()
            val enabled = props.getProperty("MAAS_ENABLED", "true").toBoolean()

            if (!enabled || apiKey.isEmpty() || baseUrl.isEmpty() || model.isEmpty()) {
                null
            } else {
                Config(apiKey = apiKey, baseUrl = baseUrl, model = model)
            }
        } catch (_: Throwable) {
            null
        }
    }
}
