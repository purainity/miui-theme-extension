package me.purainity.miuithemeextension.ui

import me.purainity.miuithemeextension.config.ModuleConfig
import me.purainity.miuithemeextension.config.Setting
import org.junit.Assert.*
import org.junit.Test

class SettingsRowPresenterTest {
    @Test fun checkedUsesComponentStateOnlyForIconRow() {
        val config = ModuleConfig(values = mapOf(Setting.MASTER to true, Setting.RESOURCES to true, Setting.HIDE_ICON to true))
        assertTrue(SettingsRowPresenter.checked(Setting.RESOURCES, config, iconHidden = false))
        assertTrue(SettingsRowPresenter.checked(Setting.HIDE_ICON, config, iconHidden = true))
        // 图标行忽略存储值，以组件实际状态为准
        assertFalse(SettingsRowPresenter.checked(Setting.HIDE_ICON, config, iconHidden = false))
    }
}
