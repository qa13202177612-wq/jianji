package com.jianji.app

import android.app.Application
import com.jianji.app.data.AppDatabase
import com.jianji.app.data.BillRepository
import com.jianji.app.data.SettingsRepository

class JianJiApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: BillRepository
        private set

    lateinit var settings: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.get(this)
        repository = BillRepository(database.billDao())
        settings = SettingsRepository(this)
    }
}
