package com.jianji.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jianji_settings")

data class AppSettings(
    /** 每月预算，单位分；0 表示未设置 */
    val budgetCents: Long = 0L,
    /** 是否启用短信自动记账 */
    val autoSms: Boolean = true,
    /** 0 跟随系统，1 浅色，2 深色 */
    val themeMode: Int = THEME_SYSTEM
) {
    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BUDGET = longPreferencesKey("monthly_budget_cents")
        val AUTO_SMS = booleanPreferencesKey("auto_sms")
        val THEME = intPreferencesKey("theme_mode")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch {
            // 配置文件损坏时回退到默认值，绝不让应用启动崩溃
            emit(emptyPreferences())
        }
        .map { prefs ->
            AppSettings(
                budgetCents = (prefs[Keys.BUDGET] ?: 0L).coerceAtLeast(0L),
                autoSms = prefs[Keys.AUTO_SMS] ?: true,
                themeMode = prefs[Keys.THEME] ?: AppSettings.THEME_SYSTEM
            )
        }

    /** 供短信接收器一次性读取 */
    suspend fun snapshot(): AppSettings = settings.first()

    suspend fun setBudget(cents: Long) {
        context.dataStore.edit { it[Keys.BUDGET] = cents.coerceAtLeast(0L) }
    }

    suspend fun setAutoSms(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SMS] = enabled }
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { it[Keys.THEME] = mode }
    }
}
