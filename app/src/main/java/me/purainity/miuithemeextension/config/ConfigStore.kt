package me.purainity.miuithemeextension.config

import android.content.Context
import android.net.Uri

/** 配置跨进程契约（8.6）：配置读取 URI 与作用域宿主集合 */
object ConfigContract {
    const val AUTHORITY = "me.purainity.miuithemeextension.config"
    // 宿主经 ContentResolver 查询的只读配置入口
    val CONFIG_URI: Uri = Uri.parse("content://$AUTHORITY/config")
    // 作用域内四个宿主进程：主题壁纸、智能助理、系统桌面与 system_server（1）
    val HOSTS = setOf("com.android.thememanager", "com.miui.personalassistant", "com.miui.home", "android")
}

/** 配置存储（8.6 写入侧）：设备保护存储的 SharedPreferences，写入递增 revision 并 notifyChange 广播变更 */
class ConfigStore(context: Context) {
    // 设备保护存储（Direct Boot 可用）：provider 为 directBootAware，开机未解锁阶段也能读到配置（8.6）
    private val storage = context.createDeviceProtectedStorageContext()
    private val preferences = storage.getSharedPreferences("module_settings", Context.MODE_PRIVATE)
    /** 读取当前配置快照（revision 与各开关值） */
    fun read(): ModuleConfig = ModuleConfig(preferences.getLong("revision", 0),
        Setting.entries.associateWith { preferences.getBoolean(it.key, it.defaultValue) })

    /** 写入单个开关：值变化时 revision+1 并 notifyChange；值未变化直接视为成功（8.6） */
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
