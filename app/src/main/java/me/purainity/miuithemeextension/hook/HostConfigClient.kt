package me.purainity.miuithemeextension.hook

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.HandlerThread
import me.purainity.miuithemeextension.config.*
import java.util.concurrent.CopyOnWriteArrayList

/** 宿主进程内的配置客户端（8.6）：经 ContentProvider 拉取快照，注册观察者接管后续变更通知 */
class HostConfigClient(private val host: String) {
    @Volatile var config = ModuleConfig()
        private set
    @Volatile var context: Context? = null
        private set
    // 配置读取专用线程，异步拉取不阻塞宿主调用线程
    private val thread by lazy { HandlerThread("MIUIThemeExtension-config").apply { start() } }
    val handler by lazy { Handler(thread.looper) }
    private var observing = false
    private var loaded = false
    private var configError = false
    // 上下文就绪回调与配置变更监听
    val onAttached = CopyOnWriteArrayList<(Context) -> Unit>()
    val listeners = CopyOnWriteArrayList<(ModuleConfig) -> Unit>()
    private val observer by lazy { object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) { refresh() }
    } }
    /** 记录宿主上下文并确保配置被拉取：eager 同步执行，否则投递到配置线程 */
    @Synchronized fun connect(value: Context, eager: Boolean = false) {
        if (context == null) {
            context = value.applicationContext ?: value
            onAttached.forEach { callback ->
                try { callback(requireNotNull(context)) } catch (e: Exception) { HookLog.error("host startup", e) }
            }
        }
        // 配置从未成功读取时在边界调用处重试拉取
        // eager 同步执行：validateTheme 入口系统必然就绪，binder 线程短暂阻塞可接受，确保本帧窗口的租借判定读到真实配置
        // 其余入口保持异步，不拖慢宿主启动
        // 成功读取后由 ContentObserver 接管变更通知，不再重复拉取
        if (loaded && !configError) return
        if (eager) refresh() else handler.post(::refresh)
    }
    /** 宿主回到前台或即将进入详情时拉取一次，补偿后台阶段被 MIUI 延迟的观察者回调（8.6） */
    fun refreshAtForegroundBoundary() = refresh()

    /** 拉取配置快照：失败回退默认配置并保持全关（fail-closed，8.6）；仅在首次加载或配置变化时通知监听 */
    @Synchronized
    private fun refresh() {
        val resolver = context?.contentResolver ?: return
        val previous = config
        try {
            // 首次拉取时注册观察者，注册后立即查询刷新内存缓存（8.6）
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
            // 读取失败按全关配置处理（master 默认关闭）且只记一次日志（8.6）
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
