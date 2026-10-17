package me.purainity.miuithemeextension.config

/** 全部开关（3.1）：key 用作存储与跨进程传输的键名，defaultValue 为初装默认值 */
enum class Setting(val key: String, val defaultValue: Boolean = false) {
    MASTER("master_enabled"),
    THIRD_THEME("allow_third_party_themes", true),
    RESOURCES("unlock_paid_resources"),
    PAID_BADGES("hide_paid_badges", true),
    LARGE_ICONS("unlock_large_icons"),
    WIDGETS("unlock_paid_widgets"),
    WIDGET_BADGES("hide_widget_pay_badges", true),
    ADS("hide_theme_ads"),
    HIDE_ICON("hide_launcher_icon");
}

/** 配置快照（8.6）：version 为变更序号 revision，values 为各开关的原始值 */
data class ModuleConfig(val version: Long = 0, val values: Map<Setting, Boolean> = emptyMap()) {
    // 缺失项回退开关默认值
    operator fun get(setting: Setting): Boolean = values[setting] ?: setting.defaultValue
    /** 生效判定（3.3）：总开关之下叠加从属关系 */
    fun enabled(setting: Setting): Boolean = this[Setting.MASTER] && when (setting) {
        // 解锁付费主题资源从属于第三方主题开关：数据层改写依赖框架层 DRM 窗口持续放行才能持久生效
        Setting.RESOURCES -> this[setting] && this[Setting.THIRD_THEME]
        // 价格控件实测仅被主题/字体卡片使用，隐藏主题付费标识只从属于主题资源解锁
        Setting.PAID_BADGES -> this[setting] && this[Setting.RESOURCES] && this[Setting.THIRD_THEME]
        Setting.WIDGET_BADGES -> this[setting] && this[Setting.WIDGETS]
        else -> this[setting]
    }
    /** 可交互判定（3.2）：依赖的上级开关关闭时子开关禁用 */
    fun interactive(setting: Setting): Boolean = setting == Setting.MASTER ||
        this[Setting.MASTER] && when (setting) {
            Setting.RESOURCES -> this[Setting.THIRD_THEME]
            Setting.PAID_BADGES -> this[Setting.RESOURCES] && this[Setting.THIRD_THEME]
            Setting.WIDGET_BADGES -> this[Setting.WIDGETS]
            else -> true
        }
}

/** 资源类别（8.1）：普通资源、大图标、MAML（小组件与超级壁纸）与未知（永不改写） */
enum class ResourceKind { STANDARD, LARGE_ICON, MAML, UNSUPPORTED }

/** 资源分类与放行策略（8.1、8.2） */
object ResourcePolicy {
    // 大图标子串：必须先于普通资源词判定，防止 `largeicons` 被 `icon` 吞掉
    private val largeIconTokens = setOf("largeicon", "large_icon")
    // MAML 子串：必须先于普通资源词判定，防止 spwallpaper→wallpaper、spaod→aod 误吞
    private val mamlTokens = setOf("maml", "widget", "spwallpaper", "splockscreen", "spaod", "superwallpaper")
    // 普通资源子串：覆盖全部具备收费能力的类别，含当前免费但保留付费能力的壁纸、息屏、铃声等（7）
    // 复合词（videowallpaper/miwallpaper/mmsringtone 等）由子串自动覆盖
    private val standardTokens = setOf(
        "theme", "font", "wallpaper", "lockscreen", "lockstyle", "icons", "icon",
        "hybrid", "aod", "ringtone", "notification", "alarm", "bootaudio", "audioeffect",
    )

    /** 按类别字符串的小写子串有序匹配判定资源类别（8.1） */
    fun kind(category: String): ResourceKind {
        val value = category.lowercase(java.util.Locale.ROOT)
        return when {
            largeIconTokens.any { value.contains(it) } -> ResourceKind.LARGE_ICON
            mamlTokens.any { value.contains(it) } -> ResourceKind.MAML
            standardTokens.any { value.contains(it) } -> ResourceKind.STANDARD
            else -> ResourceKind.UNSUPPORTED
        }
    }
    /** 数据层注入判定（8.2）：productId 空白一律放行原逻辑（探测保护），非空白按类别对应开关注入 */
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
    /** 商品 id 安全校验（8.5）：非空、长度 ≤180、无路径分隔符与控制字符 */
    fun safeProductId(id: String): Boolean = id.isNotBlank() && id.length <= 180 &&
        id != "." && id != ".." && id.none { it == '/' || it == '\\' || it.isISOControl() }
}
