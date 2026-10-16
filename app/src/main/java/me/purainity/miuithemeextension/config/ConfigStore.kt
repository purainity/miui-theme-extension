package me.purainity.miuithemeextension.config

import android.content.Context
import android.net.Uri

object ConfigContract {
    const val AUTHORITY = "me.purainity.miuithemeextension.config"
    val CONFIG_URI: Uri = Uri.parse("content://$AUTHORITY/config")
    val HOSTS = setOf("com.android.thememanager", "com.miui.personalassistant", "com.miui.home", "android")
}

class ConfigStore(context: Context) {
    private val storage = context.createDeviceProtectedStorageContext()
    private val preferences = storage.getSharedPreferences("module_settings", Context.MODE_PRIVATE)
    fun read(): ModuleConfig = ModuleConfig(preferences.getLong("revision", 0),
        Setting.entries.associateWith { preferences.getBoolean(it.key, it.defaultValue) })

    fun write(setting: Setting, value: Boolean): Boolean = synchronized(LOCK) {
        if (preferences.getBoolean(setting.key, setting.defaultValue) == value) return true
        val revision = preferences.getLong("revision", 0) + 1
        val saved = preferences.edit().putBoolean(setting.key, value).putLong("revision", revision).commit()
        if (saved) {
            storage.contentResolver.notifyChange(ConfigContract.CONFIG_URI, null)
        }
        saved
    }

    private companion object {
        val LOCK = Any()
    }
}
