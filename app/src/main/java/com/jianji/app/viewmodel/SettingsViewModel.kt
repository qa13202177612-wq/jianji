package com.jianji.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jianji.app.JianJiApp
import com.jianji.app.data.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as JianJiApp).settings

    val settings = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    fun setBudget(cents: Long) {
        viewModelScope.launch {
            runCatching { repository.setBudget(cents) }
        }
    }

    fun setAutoSms(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setAutoSms(enabled) }
        }
    }

    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            runCatching { repository.setThemeMode(mode) }
        }
    }
}
