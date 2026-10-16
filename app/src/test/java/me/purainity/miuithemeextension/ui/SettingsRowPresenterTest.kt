package me.purainity.miuithemeextension.ui

import me.purainity.miuithemeextension.R
import me.purainity.miuithemeextension.config.ModuleConfig
import me.purainity.miuithemeextension.config.Setting
import org.junit.Assert.*
import org.junit.Test

class SettingsRowPresenterTest {
    @Test fun dependencyHintsFollowUnlockSwitches() {
        val allOff = ModuleConfig()
        assertEquals(R.string.need_resource, SettingsRowPresenter.dependencyHint(Setting.PAID_BADGES, allOff))
        assertEquals(R.string.need_widget, SettingsRowPresenter.dependencyHint(Setting.WIDGET_BADGES, allOff))
        assertNull(SettingsRowPresenter.dependencyHint(Setting.MASTER, allOff))
        assertNull(SettingsRowPresenter.dependencyHint(Setting.ADS, allOff))
        assertNull(SettingsRowPresenter.dependencyHint(Setting.HIDE_ICON, allOff))
        // 主题或大图标任一解锁即可解除隐藏主题付费标识的依赖
        assertNull(SettingsRowPresenter.dependencyHint(Setting.PAID_BADGES,
            ModuleConfig(values = mapOf(Setting.RESOURCES to true))))
        assertNull(SettingsRowPresenter.dependencyHint(Setting.PAID_BADGES,
            ModuleConfig(values = mapOf(Setting.LARGE_ICONS to true))))
        assertNull(SettingsRowPresenter.dependencyHint(Setting.WIDGET_BADGES,
            ModuleConfig(values = mapOf(Setting.WIDGETS to true))))
    }
    @Test fun summaryAppendsHintOnNewLine() {
        assertEquals("效果", SettingsRowPresenter.summary("效果", null))
        assertEquals("效果\n提示", SettingsRowPresenter.summary("效果", "提示"))
    }
    @Test fun checkedUsesComponentStateOnlyForIconRow() {
        val config = ModuleConfig(values = mapOf(Setting.MASTER to true, Setting.RESOURCES to true, Setting.HIDE_ICON to true))
        assertTrue(SettingsRowPresenter.checked(Setting.RESOURCES, config, iconHidden = false))
        assertTrue(SettingsRowPresenter.checked(Setting.HIDE_ICON, config, iconHidden = true))
        // 图标行忽略存储值，以组件实际状态为准
        assertFalse(SettingsRowPresenter.checked(Setting.HIDE_ICON, config, iconHidden = false))
    }
}
