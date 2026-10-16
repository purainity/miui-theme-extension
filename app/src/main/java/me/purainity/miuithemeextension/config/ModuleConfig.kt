package me.purainity.miuithemeextension.config

enum class Setting(val key: String, val defaultValue: Boolean = false) {
    MASTER("master_enabled"),
    RESOURCES("unlock_paid_resources"),
    PAID_BADGES("hide_paid_badges", true),
    LARGE_ICONS("unlock_large_icons"),
    WIDGETS("unlock_paid_widgets"),
    WIDGET_BADGES("hide_widget_pay_badges", true),
    ADS("hide_theme_ads"),
    HIDE_ICON("hide_launcher_icon");
}

data class ModuleConfig(val version: Long = 0, val values: Map<Setting, Boolean> = emptyMap()) {
    operator fun get(setting: Setting): Boolean = values[setting] ?: setting.defaultValue
    fun enabled(setting: Setting): Boolean = this[Setting.MASTER] && when (setting) {
        // 价格控件实测仅被主题/字体卡片使用，隐藏主题付费标识只从属于主题资源解锁
        Setting.PAID_BADGES -> this[setting] && this[Setting.RESOURCES]
        Setting.WIDGET_BADGES -> this[setting] && this[Setting.WIDGETS]
        else -> this[setting]
    }
    fun interactive(setting: Setting): Boolean = setting == Setting.MASTER ||
        this[Setting.MASTER] && when (setting) {
            Setting.PAID_BADGES -> this[Setting.RESOURCES]
            Setting.WIDGET_BADGES -> this[Setting.WIDGETS]
            else -> true
        }
}

enum class ResourceKind { STANDARD, LARGE_ICON, MAML, UNSUPPORTED }

object ResourcePolicy {
    // 大图标子串：必须先于普通资源词判定，防止 "largeicons" 被 "icon" 吞掉
    private val largeIconTokens = setOf("largeicon", "large_icon")
    // MAML 子串：必须先于普通资源词判定，防止 spwallpaper→wallpaper、spaod→aod 误吞
    private val mamlTokens = setOf("maml", "widget", "spwallpaper", "splockscreen", "spaod", "superwallpaper")
    // 普通资源子串：覆盖全部具备收费能力的类别（含当前免费但保留付费能力的壁纸、息屏、铃声等，
    // 见规划文档第 7 节类别域）；复合词（videowallpaper/miwallpaper/mmsringtone 等）由子串自动覆盖
    private val standardTokens = setOf(
        "theme", "font", "wallpaper", "lockscreen", "lockstyle", "icons", "icon",
        "hybrid", "aod", "ringtone", "notification", "alarm", "bootaudio", "audioeffect",
    )

    fun kind(category: String): ResourceKind {
        val value = category.lowercase(java.util.Locale.ROOT)
        return when {
            largeIconTokens.any { value.contains(it) } -> ResourceKind.LARGE_ICON
            mamlTokens.any { value.contains(it) } -> ResourceKind.MAML
            standardTokens.any { value.contains(it) } -> ResourceKind.STANDARD
            else -> ResourceKind.UNSUPPORTED
        }
    }
    fun allows(config: ModuleConfig, productId: String?, kind: ResourceKind): Boolean {
        // 探测对象永远不会有 productId；即使在应用任务执行期间也保持原值放行
        if (productId.isNullOrBlank()) return false
        return config.enabled(when (kind) {
            ResourceKind.STANDARD -> Setting.RESOURCES
            ResourceKind.LARGE_ICON -> Setting.LARGE_ICONS
            ResourceKind.MAML -> Setting.WIDGETS
            ResourceKind.UNSUPPORTED -> return false
        })
    }
    fun safeProductId(id: String): Boolean = id.isNotBlank() && id.length <= 180 &&
        id != "." && id != ".." && id.none { it == '/' || it == '\\' || it.isISOControl() }
}
