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
        Setting.PAID_BADGES -> this[setting] && (this[Setting.RESOURCES] || this[Setting.LARGE_ICONS])
        Setting.WIDGET_BADGES -> this[setting] && this[Setting.WIDGETS]
        else -> this[setting]
    }
    fun interactive(setting: Setting): Boolean = setting == Setting.MASTER ||
        this[Setting.MASTER] && when (setting) {
            Setting.PAID_BADGES -> this[Setting.RESOURCES] || this[Setting.LARGE_ICONS]
            Setting.WIDGET_BADGES -> this[Setting.WIDGETS]
            else -> true
        }
}

enum class ResourceKind { STANDARD, LARGE_ICON, MAML, UNSUPPORTED }

object ResourcePolicy {
    fun kind(category: String): ResourceKind {
        val parts = category.lowercase(java.util.Locale.ROOT).split('/', '-', '_', '.', ':')
        if (category.lowercase(java.util.Locale.ROOT) in setOf("largeicons", "large_icons", "largeicon"))
            return ResourceKind.LARGE_ICON
        if (parts.any { it in setOf("maml", "widget", "widgets", "spwallpaper", "splockscreen", "spaod", "superwallpaper") })
            return ResourceKind.MAML
        return if (parts.any { it in setOf("theme", "themes", "font", "fonts", "wallpaper", "wallpapers", "lockscreen", "icons", "icon") })
            ResourceKind.STANDARD else ResourceKind.UNSUPPORTED
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
