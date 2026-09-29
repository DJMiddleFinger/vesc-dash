package com.vescdash

import android.app.Application
import com.vescdash.data.SettingsStore
import com.vescdash.data.VescRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class VescDashApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var store: SettingsStore
        private set
    lateinit var repository: VescRepository
        private set

    override fun onCreate() {
        super.onCreate()
        store = SettingsStore(this)
        repository = VescRepository(this, store, appScope)
    }
}
