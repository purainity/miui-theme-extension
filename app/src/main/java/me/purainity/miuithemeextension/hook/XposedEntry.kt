package me.purainity.miuithemeextension.hook

import android.app.Activity
import android.app.Application
import android.content.Context
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import me.purainity.miuithemeextension.config.ConfigContract

/** 经典 Xposed 入口；应用宿主与系统框架的 Hook 在各自进程内独立安装 */
class XposedEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
        val host = param.packageName
        val loader = param.classLoader ?: return
        if (host == "me.purainity.miuithemeextension") {
            try {
                val status = Class.forName("me.purainity.miuithemeextension.ModuleStatus", false, loader)
                XposedBridge.hookMethod(status.getDeclaredMethod("isActive"), object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) { param.result = true }
                })
            } catch (e: Throwable) { HookLog.error("activation", e) }
            return
        }
        if (host !in ConfigContract.HOSTS) return
        val client = HostConfigClient(host)
        val hooks = Hooks(loader, client)
        try {
            if (host == "android") SystemHooks(hooks).install()
            else {
                hooks.install(Application::class.java.getDeclaredMethod("attach", Context::class.java), after = { call ->
                    if (!call.hasThrowable()) client.connect(call.args[0] as Context)
                })
                // 从设置页返回宿主或新开详情时同步拉取，避免后台阶段的观察者延迟留下旧配置。
                hooks.install(Activity::class.java.getDeclaredMethod("onResume"), before = {
                    client.refreshAtForegroundBoundary()
                })
                when (host) {
                    "com.android.thememanager" -> ThemeHooks(hooks).install(param.appInfo?.sourceDir, param.appInfo?.splitSourceDirs.orEmpty())
                    "com.miui.personalassistant" -> WidgetHooks.assistant(hooks)
                    "com.miui.home" -> WidgetHooks.home(hooks)
                }
            }
        } catch (e: Throwable) { HookLog.error("entry/$host", e) }
    }
}
