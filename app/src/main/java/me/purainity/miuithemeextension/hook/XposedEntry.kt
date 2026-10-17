package me.purainity.miuithemeextension.hook

import android.app.Activity
import android.app.Application
import android.content.Context
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import me.purainity.miuithemeextension.config.ConfigContract

/** 经典 Xposed 入口；按宿主进程分发（5.1），system_server 与应用宿主的 Hook 在各自进程内独立安装 */
class XposedEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
        val host = param.packageName
        val loader = param.classLoader ?: return
        if (host == "me.purainity.miuithemeextension") {
            // 激活自检：不 Hook 任何方法——经应用 classloader 反射置位静态标志（8.7）。
            // 必须经 param.classLoader 取类：新框架的模块类加载器与宿主 classpath 隔离，直接静态赋值有类身份分裂风险。
            // 经应用 classloader 反射保证与 UI 读取的是同一个 Class 实例
            try {
                val status = Class.forName("me.purainity.miuithemeextension.ModuleStatus", false, loader)
                status.getDeclaredMethod("markActive").invoke(null)
                // 框架 API 版本只能在此读取：XposedBridge 由模块类加载器解析，应用自身 classloader 反射不可达（UI 侧 Class.forName 必失败）
                // 故经同一反射通道带回 UI 进程，失败不影响激活置位
                val version = try { XposedBridge.getXposedVersion() } catch (e: Throwable) { HookLog.error("xposed version", e); null }
                if (version != null) status.getDeclaredMethod("reportXposedApi", Int::class.javaPrimitiveType).invoke(null, version)
            } catch (e: Throwable) { HookLog.error("activation", e) }
            return
        }
        if (host !in ConfigContract.HOSTS) return
        val client = HostConfigClient(host)
        val hooks = Hooks(loader, client)
        try {
            if (host == "android") SystemHooks(hooks).install()
            else {
                // 拿到宿主 Context 后连接配置客户端（5.1、8.6）
                hooks.install(Application::class.java.getDeclaredMethod("attach", Context::class.java), after = { call ->
                    if (!call.hasThrowable()) client.connect(call.args[0] as Context)
                })
                // 从设置页返回宿主或新开详情时同步拉取，避免后台阶段的观察者延迟留下旧配置（8.6）
                hooks.install(Activity::class.java.getDeclaredMethod("onResume"), before = {
                    client.refreshAtForegroundBoundary()
                })
                // 按宿主分发业务 Hook（6.1、6.3、6.4）
                when (host) {
                    "com.android.thememanager" -> ThemeHooks(hooks).install(param.appInfo?.sourceDir, param.appInfo?.splitSourceDirs.orEmpty())
                    "com.miui.personalassistant" -> WidgetHooks.assistant(hooks)
                    "com.miui.home" -> WidgetHooks.home(hooks)
                }
            }
        } catch (e: Throwable) { HookLog.error("entry/$host", e) }
    }
}
