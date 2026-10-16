package me.purainity.miuithemeextension

import android.app.Application
import com.google.android.material.color.DynamicColors

class ModuleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}

object ModuleStatus {
    @JvmStatic fun isActive(): Boolean = false
}
