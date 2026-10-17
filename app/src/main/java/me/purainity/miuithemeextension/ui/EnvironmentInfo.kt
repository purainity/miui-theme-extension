package me.purainity.miuithemeextension.ui

import me.purainity.miuithemeextension.R

/** 环境信息条目：标签字符串资源 id 与内容文本 */
data class EnvironmentEntry(val label: Int, val value: String)

/** 宿主包版本快照 */
data class HostPackage(val versionName: String?, val versionCode: Long)

/**
 * 版本信息卡片的数据源。
 * 系统属性读取、宿主包查询与字符串解析全部以函数注入，回退与异常分支可在单元测试中覆盖。
 * 约定：宿主未安装（查询返回 null）显示“未安装”，查询抛异常或版本名缺失显示“无法读取”
 */
class EnvironmentInfo(
    private val resolve: (Int) -> String,
    private val format: (Int, Array<out Any>) -> String,
    private val moduleVersionName: String,
    private val moduleVersionCode: Long,
    private val androidRelease: String,
    private val androidSdk: Int,
    private val miuiVersion: String,
    private val xposedApiVersion: String?,
    private val systemProperty: (String) -> String,
    private val fallbackModel: String,
    private val device: String,
    private val hostLookup: (String) -> HostPackage?
) {
    /** 按界面顺序返回全部条目：模块版本、设备型号、系统版本、MIUI 版本、Xposed API 版本、三个宿主版本 */
    fun entries(): List<EnvironmentEntry> = listOf(
        EnvironmentEntry(R.string.module_version, version(moduleVersionName, moduleVersionCode)),
        EnvironmentEntry(R.string.device_model, deviceModel()),
        EnvironmentEntry(R.string.android_version, format(R.string.android_format, arrayOf<Any>(androidRelease, androidSdk))),
        EnvironmentEntry(R.string.miui_version, miuiVersion),
        EnvironmentEntry(R.string.xposed_api_version, xposedApiVersion ?: resolve(R.string.not_detected))
    ) + hosts.map { (label, pkg) -> EnvironmentEntry(label, hostVersion(pkg)) }

    /** 设备型号：营销名（ro.product.marketname）为空白时回退到 Build.MODEL */
    private fun deviceModel(): String {
        val market = systemProperty(MARKET_NAME).takeIf { it.isNotBlank() } ?: fallbackModel
        return format(R.string.model_format, arrayOf(market, device))
    }

    /** 宿主版本：未安装显示“未安装”，读取失败或版本名缺失显示“无法读取” */
    private fun hostVersion(pkg: String): String = try {
        val info = hostLookup(pkg) ?: return resolve(R.string.not_installed)
        version(info.versionName ?: resolve(R.string.unknown_version), info.versionCode)
    } catch (e: Exception) { resolve(R.string.unknown_version) }

    private fun version(name: String, code: Long): String =
        format(R.string.version_format, arrayOf<Any>(name, code))

    private companion object {
        const val MARKET_NAME = "ro.product.marketname"
        // 三个宿主版本条目：主题壁纸、智能助理、系统桌面（4.2）
        val hosts = listOf(
            R.string.host_theme to "com.android.thememanager",
            R.string.host_assistant to "com.miui.personalassistant",
            R.string.host_home to "com.miui.home"
        )
    }
}
