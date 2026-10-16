package me.purainity.miuithemeextension.hook

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.HandlerThread
import me.purainity.miuithemeextension.config.*
import java.util.concurrent.CopyOnWriteArrayList

class HostConfigClient(private val host: String) {
    @Volatile var config = ModuleConfig()
        private set
    @Volatile var context: Context? = null
        private set
    private val thread by lazy { HandlerThread("MIUIThemeExtension-config").apply { start() } }
    val handler by lazy { Handler(thread.looper) }
    private var observing = false
    private var loaded = false
    private var configError = false
    val onAttached = CopyOnWriteArrayList<(Context) -> Unit>()
    val listeners = CopyOnWriteArrayList<(ModuleConfig) -> Unit>()
    private val observer by lazy { object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) { refresh() }
    } }
    @Synchronized fun connect(value: Context) {
        if (context != null) return
        context = value.applicationContext ?: value
        onAttached.forEach { callback ->
            try { callback(requireNotNull(context)) } catch (e: Exception) { HookLog.error("host startup", e) }
        }
        handler.post(::refresh)
    }
    /** 宿主回到前台或即将进入详情时拉取一次，补偿后台状态下被 MIUI 延迟的观察者回调。 */
    fun refreshAtForegroundBoundary() = refresh()

    @Synchronized
    private fun refresh() {
        val resolver = context?.contentResolver ?: return
        val previous = config
        try {
            if (!observing) {
                resolver.registerContentObserver(ConfigContract.CONFIG_URI, false, observer)
                observing = true
            }
            val snapshot = resolver.query(ConfigContract.CONFIG_URI, null, null, null, null)?.use { cursor ->
                check(cursor.moveToFirst()) { "Empty settings" }
                ModuleConfig(cursor.getLong(cursor.getColumnIndexOrThrow("revision")), Setting.entries.associateWith {
                    cursor.getInt(cursor.getColumnIndexOrThrow(it.key)) != 0
                })
            } ?: error("Settings unavailable")
            config = snapshot
            configError = false
        } catch (e: Exception) {
            config = ModuleConfig()
            if (!configError) HookLog.error("config/$host", e)
            configError = true
        }
        if (!loaded || previous != config) listeners.forEach { listener ->
            try { listener(config) } catch (e: Exception) { HookLog.error("config listener", e) }
        }
        loaded = true
    }
}
