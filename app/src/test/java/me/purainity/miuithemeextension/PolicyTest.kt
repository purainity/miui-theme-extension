package me.purainity.miuithemeextension

import me.purainity.miuithemeextension.config.*
import org.junit.Assert.*
import org.junit.Test

class PolicyTest {
    @Test fun defaultsAndDependencies() {
        val off = ModuleConfig()
        assertTrue(off[Setting.PAID_BADGES]); assertTrue(off[Setting.WIDGET_BADGES])
        // 允许使用第三方主题与隐藏主题商店广告默认开启（使用性与纯展示层功能），其余解锁类开关默认关闭
        assertTrue(off[Setting.THIRD_THEME]); assertTrue(off[Setting.ADS])
        Setting.entries.forEach { assertFalse(off.enabled(it)) }
        val config = ModuleConfig(values = mapOf(Setting.MASTER to true, Setting.WIDGETS to true))
        assertTrue(config.enabled(Setting.WIDGET_BADGES))
        assertFalse(config.enabled(Setting.PAID_BADGES))
        assertFalse(config.interactive(Setting.PAID_BADGES))
        assertTrue(config[Setting.PAID_BADGES]) // 关闭父开关时保留子开关的存储值
    }
    @Test fun resourcesFollowThirdPartyTheme() {
        // 解锁付费主题资源从属于第三方主题开关：显式关闭后失效，但存储值保留、不可交互
        val blocked = ModuleConfig(values = mapOf(
            Setting.MASTER to true, Setting.THIRD_THEME to false, Setting.RESOURCES to true, Setting.PAID_BADGES to true))
        assertFalse(blocked.enabled(Setting.RESOURCES))
        assertFalse(blocked.interactive(Setting.RESOURCES))
        assertFalse(blocked.enabled(Setting.PAID_BADGES))
        assertFalse(blocked.interactive(Setting.PAID_BADGES))
        assertTrue(blocked[Setting.RESOURCES]) // 存储值保留
        // 第三方主题开关独立生效（无需解锁付费资源），数据层不生效
        val thirdOnly = ModuleConfig(values = mapOf(Setting.MASTER to true, Setting.THIRD_THEME to true))
        assertTrue(thirdOnly.enabled(Setting.THIRD_THEME))
        assertFalse(thirdOnly.enabled(Setting.RESOURCES))
        assertTrue(thirdOnly.interactive(Setting.RESOURCES))
        // 两者都开启时全链生效
        val both = ModuleConfig(values = mapOf(
            Setting.MASTER to true, Setting.THIRD_THEME to true, Setting.RESOURCES to true, Setting.PAID_BADGES to true))
        assertTrue(both.enabled(Setting.RESOURCES))
        assertTrue(both.enabled(Setting.PAID_BADGES))
    }
    @Test fun paidBadgesFollowsResourcesOnly() {
        // 价格控件实测仅被主题/字体卡片使用，隐藏主题付费标识只从属于主题资源解锁，与大图标无关
        val resources = ModuleConfig(values = mapOf(Setting.MASTER to true, Setting.RESOURCES to true, Setting.PAID_BADGES to true))
        assertTrue(resources.enabled(Setting.PAID_BADGES))
        assertTrue(resources.interactive(Setting.PAID_BADGES))
        val largeIconsOnly = ModuleConfig(values = mapOf(Setting.MASTER to true, Setting.LARGE_ICONS to true, Setting.PAID_BADGES to true))
        assertFalse(largeIconsOnly.enabled(Setting.PAID_BADGES))
        assertFalse(largeIconsOnly.interactive(Setting.PAID_BADGES))
        assertTrue(largeIconsOnly[Setting.PAID_BADGES]) // 仅交互禁用，存储值保留
    }
    @Test fun independentResourcesAndProbeObjects() {
        val gates = mapOf(ResourceKind.STANDARD to Setting.RESOURCES, ResourceKind.LARGE_ICON to Setting.LARGE_ICONS, ResourceKind.MAML to Setting.WIDGETS)
        for ((kind, setting) in gates) {
            val config = ModuleConfig(values = mapOf(Setting.MASTER to true, setting to true))
            for (other in ResourceKind.entries) assertEquals(kind == other, ResourcePolicy.allows(config, "12345", other))
            assertFalse(ResourcePolicy.allows(config, null, kind))
            assertFalse(ResourcePolicy.allows(config, "", kind))
            assertFalse(ResourcePolicy.allows(config, "  ", kind))
        }
    }
    @Test fun categoryDomainClassification() {
        // 大图标：含大小写与下划线变体
        listOf("largeicons", "Largeicon", "LARGEICONS", "large_icons").forEach {
            assertEquals(ResourceKind.LARGE_ICON, ResourcePolicy.kind(it))
        }
        // MAML：小组件与超级壁纸
        listOf("maml", "widget", "widgets", "widget_suit", "WIDGET_SUIT", "spwallpaper",
            "splockscreen", "spaod", "SuperWallpaper", "superwallpaper").forEach {
            assertEquals(ResourceKind.MAML, ResourcePolicy.kind(it))
        }
        // 普通资源：主题族与字体
        listOf("theme", "themes", "Theme", "hybrid", "HYBRID", "fonts", "Font", "FONT").forEach {
            assertEquals(ResourceKind.STANDARD, ResourcePolicy.kind(it))
        }
        // 普通资源：壁纸族（非 sp 前缀）、锁屏族与锁屏样式
        listOf("wallpaper", "wallpaper_fold", "wallpaper_inner", "videowallpaper", "dynamicvideowallpaper",
            "miwallpaper", "miwallpaper_inner", "Video_Wallpaper", "LIVE_WALLPAPER", "DeskWallpaper", "MiWallpaper",
            "lockscreen", "lockscreen_fold", "lockscreen_inner", "lockscreen_outer", "lockstyle", "lockstyle_pad",
            "lockstyle_inner", "lockstyle_outer", "LockStyle").forEach {
            assertEquals(ResourceKind.STANDARD, ResourcePolicy.kind(it))
        }
        // 普通资源：图标、息屏、铃声族（含无分隔符复合词）
        listOf("icons", "Icon", "ICONS", "aod", "AOD", "Aod", "ringtone", "ringtones", "mmsringtone",
            "calendarringtone", "notification", "NotificationAudio", "alarm", "alarmscreen", "AlarmAudio",
            "bootaudio", "BootAudio", "audioeffect", "AudioEffect").forEach {
            assertEquals(ResourceKind.STANDARD, ResourcePolicy.kind(it))
        }
        // 非独立商品：主题内子模块与桌面 gadget 元素不做处理
        listOf("unknown", "", "thirdapp", "framework-res", "launcher", "statusbar", "contact", "Mms",
            "incallshow", "BootAnimation", "boots", "animation", "clock_1x2", "Clock_2x2", "photo_frame_2x4",
            "HOVER", "POP").forEach {
            assertEquals(ResourceKind.UNSUPPORTED, ResourcePolicy.kind(it))
        }
    }
    @Test fun matchingOrderGuardsCompositeWords() {
        // 判定顺序回归：largeicons 不得被 icon 吞掉，sp* 与 SuperWallpaper 不得被 wallpaper/lockscreen/aod 吞掉
        assertEquals(ResourceKind.LARGE_ICON, ResourcePolicy.kind("largeicons"))
        assertEquals(ResourceKind.MAML, ResourcePolicy.kind("spwallpaper"))
        assertEquals(ResourceKind.MAML, ResourcePolicy.kind("splockscreen"))
        assertEquals(ResourceKind.MAML, ResourcePolicy.kind("spaod"))
        assertEquals(ResourceKind.MAML, ResourcePolicy.kind("SuperWallpaper"))
        // miwallpaper（桌面特效壁纸）含 wallpaper 词根但归普通资源
        assertEquals(ResourceKind.STANDARD, ResourcePolicy.kind("miwallpaper"))
    }
    @Test fun invalidFileNamesAreRejected() {
        listOf("", ".", "..", "../x", "a/b", "a\\b", "a\u0000b").forEach { assertFalse(ResourcePolicy.safeProductId(it)) }
        assertTrue(ResourcePolicy.safeProductId("9d57-12345"))
    }
}
