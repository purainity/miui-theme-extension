package me.purainity.miuithemeextension

import android.app.Application
import com.google.android.material.color.DynamicColors

class ModuleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}

/** 激活状态载体：由模块加载入口在自身进程经反射置位（见 XposedEntry），UI 直接读取 */
object ModuleStatus {
    @JvmStatic private var active = false
    @JvmStatic private var apiVersion: Int? = null
    /** 仅由 Xposed 加载入口调用；经应用 classloader 反射进入，保证与 UI 操作同一个 Class 实例 */
    @JvmStatic fun markActive() { active = true }
    /** 框架 API 版本随激活一并记录：入口进程内直接调用 XposedBridge（该类仅模块类加载器可见，应用 classloader 反射不可达） */
    @JvmStatic fun reportXposedApi(version: Int) { apiVersion = version }
    @JvmStatic fun isActive(): Boolean = active
    @JvmStatic fun xposedApiVersion(): Int? = apiVersion
}
