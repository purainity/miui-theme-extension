package me.purainity.miuithemeextension.ui

import android.content.ComponentName
import android.content.pm.PackageManager

/**
 * 桌面图标隐藏：通过启停指向主 Activity 的 activity-alias 实现（DONT_KILL_APP）
 * 与 Hook 激活状态完全无关；组件实际状态是每次回到界面时自愈回写的权威来源
 */
class IconHider(private val manager: PackageManager, private val component: ComponentName) {

    /** 组件当前是否处于隐藏（禁用）状态 */
    fun hidden(): Boolean =
        manager.getComponentEnabledSetting(component) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    /** 应用隐藏状态；失败抛出的异常由调用方回滚存储值并提示 */
    fun apply(hidden: Boolean) {
        manager.setComponentEnabledSetting(
            component,
            if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
