package me.purainity.miuithemeextension.ui

import me.purainity.miuithemeextension.R
import me.purainity.miuithemeextension.config.ModuleConfig
import me.purainity.miuithemeextension.config.Setting

/**
 * 设置行的展示逻辑（纯逻辑，便于单元测试）。
 * 与 [ModuleConfig.interactive] 配合，负责勾选值、依赖提示与说明拼接。
 */
object SettingsRowPresenter {

    /** 依赖不满足时的提示字符串资源；无依赖返回 null */
    fun dependencyHint(setting: Setting, config: ModuleConfig): Int? = when {
        setting == Setting.PAID_BADGES && !config[Setting.RESOURCES] && !config[Setting.LARGE_ICONS] -> R.string.need_resource
        setting == Setting.WIDGET_BADGES && !config[Setting.WIDGETS] -> R.string.need_widget
        else -> null
    }

    /** 行说明文案：有依赖提示时另起一行追加 */
    fun summary(effect: String, hint: String?): String =
        if (hint == null) effect else "$effect\n$hint"

    /** 开关勾选值：隐藏图标行以组件实际状态为准，其余以存储值为准 */
    fun checked(setting: Setting, config: ModuleConfig, iconHidden: Boolean): Boolean =
        if (setting == Setting.HIDE_ICON) iconHidden else config[setting]
}
