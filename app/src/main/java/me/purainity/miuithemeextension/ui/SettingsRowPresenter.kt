package me.purainity.miuithemeextension.ui

import me.purainity.miuithemeextension.config.ModuleConfig
import me.purainity.miuithemeextension.config.Setting

/**
 * 设置行的展示逻辑（纯逻辑，便于单元测试）。
 * 与 [ModuleConfig.interactive] 配合，负责勾选值；禁用行不追加提示文案。
 */
object SettingsRowPresenter {

    /** 开关勾选值：隐藏图标行以组件实际状态为准，其余以存储值为准 */
    fun checked(setting: Setting, config: ModuleConfig, iconHidden: Boolean): Boolean =
        if (setting == Setting.HIDE_ICON) iconHidden else config[setting]
}
