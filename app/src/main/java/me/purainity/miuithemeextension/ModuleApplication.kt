package me.purainity.miuithemeextension

import android.app.Application
import com.google.android.material.color.DynamicColors

/** 模块应用：为全部 Activity 应用 Material You 动态取色 */
class ModuleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}

/** 激活状态载体（8.7）：由 XposedEntry 在自身进程经反射置位，UI 直接读取 */
object ModuleStatus {
    @JvmStatic private var active = false
    @JvmStatic private var apiVersion: Int? = null
    /** 仅由 Xposed 加载入口调用；经应用 classloader 反射进入，保证与 UI 操作同一个 Class 实例 */
    @JvmStatic fun markActive() { active = true }
    /** 框架 API 版本随激活一并记录：入口进程内直接调用 XposedBridge（该类仅模块类加载器可见，应用 classloader 反射不可达） */
    @JvmStatic fun reportXposedApi(version: Int) { apiVersion = version }
    /** UI 渲染激活卡片时读取；未被框架加载时保持编译期默认 false（8.7） */
    @JvmStatic fun isActive(): Boolean = active
    /** 框架 API 版本记录值，未记录为 null（8.7） */
    @JvmStatic fun xposedApiVersion(): Int? = apiVersion
}
