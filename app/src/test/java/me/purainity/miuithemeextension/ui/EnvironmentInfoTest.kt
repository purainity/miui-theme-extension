package me.purainity.miuithemeextension.ui

import me.purainity.miuithemeextension.R
import org.junit.Assert.*
import org.junit.Test

class EnvironmentInfoTest {
    private val strings = mapOf(
        R.string.module_version to "模块版本",
        R.string.device_model to "设备型号",
        R.string.android_version to "系统版本",
        R.string.miui_version to "MIUI 版本",
        R.string.host_theme to "MIUI 主题壁纸",
        R.string.host_assistant to "MIUI 智能助理",
        R.string.host_home to "MIUI 桌面",
        R.string.version_format to "%1\$s (%2\$d)",
        R.string.model_format to "%1\$s (%2\$s)",
        R.string.android_format to "Android %1\$s (API %2\$d)",
        R.string.not_installed to "未安装",
        R.string.unknown_version to "无法读取"
    )

    private fun info(
        marketName: String = "",
        fallbackModel: String = "Fallback",
        device: String = "device",
        hostLookup: (String) -> HostPackage? = { null }
    ) = EnvironmentInfo(
        resolve = { strings.getValue(it) },
        format = { id, args -> if (args.isEmpty()) strings.getValue(id) else String.format(strings.getValue(id), *args) },
        moduleVersionName = "1.0",
        moduleVersionCode = 1L,
        androidRelease = "14",
        androidSdk = 34,
        miuiVersion = "V14.0.5.0",
        systemProperty = { name -> if (name == "ro.product.marketname") marketName else "" },
        fallbackModel = fallbackModel,
        device = device,
        hostLookup = hostLookup
    )

    @Test fun entriesFollowDisplayOrder() {
        val entries = info().entries()
        assertEquals(7, entries.size)
        val labels = entries.map { strings.getValue(it.label) }
        assertEquals(listOf("模块版本", "设备型号", "系统版本", "MIUI 版本", "MIUI 主题壁纸", "MIUI 智能助理", "MIUI 桌面"), labels)
        assertEquals("1.0 (1)", entries[0].value)
        assertEquals("Android 14 (API 34)", entries[2].value)
        assertEquals("V14.0.5.0", entries[3].value)
    }
    @Test fun deviceModelFallsBackWhenMarketNameBlank() {
        assertEquals("Fallback (device)", info(marketName = "  ").entries()[1].value)
    }
    @Test fun deviceModelPrefersMarketName() {
        assertEquals("小米 13 (device)", info(marketName = "小米 13").entries()[1].value)
    }
    @Test fun missingHostsAreNotInstalledAndFailuresAreUnreadable() {
        // 查询返回 null 视为未安装
        assertEquals("未安装", info().entries()[4].value)
        // 查询抛异常视为无法读取
        val unreadable = info(hostLookup = { throw RuntimeException("boom") }).entries()
        assertEquals("无法读取", unreadable[4].value)
        // 版本名缺失时以“无法读取”作为名称参与格式化
        assertEquals("无法读取 (5)", info(hostLookup = { HostPackage(null, 5L) }).entries()[4].value)
        assertEquals("MiuiHome (6)", info(hostLookup = { HostPackage("MiuiHome", 6L) }).entries()[6].value)
    }
}
