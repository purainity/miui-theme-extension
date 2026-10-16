package me.purainity.miuithemeextension

import me.purainity.miuithemeextension.config.*
import org.junit.Assert.*
import org.junit.Test

class PolicyTest {
    @Test fun defaultsAndDependencies() {
        val off = ModuleConfig()
        assertTrue(off[Setting.PAID_BADGES]); assertTrue(off[Setting.WIDGET_BADGES])
        Setting.entries.forEach { assertFalse(off.enabled(it)) }
        val config = ModuleConfig(values = mapOf(Setting.MASTER to true, Setting.WIDGETS to true))
        assertTrue(config.enabled(Setting.WIDGET_BADGES))
        assertFalse(config.enabled(Setting.PAID_BADGES))
        assertFalse(config.interactive(Setting.PAID_BADGES))
        assertTrue(config[Setting.PAID_BADGES]) // 关闭父开关时保留子开关的存储值
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
        assertEquals(ResourceKind.LARGE_ICON, ResourcePolicy.kind("largeicons"))
        assertEquals(ResourceKind.MAML, ResourcePolicy.kind("spwallpaper"))
        assertEquals(ResourceKind.UNSUPPORTED, ResourcePolicy.kind("unknown"))
    }
    @Test fun invalidFileNamesAreRejected() {
        listOf("", ".", "..", "../x", "a/b", "a\\b", "a\u0000b").forEach { assertFalse(ResourcePolicy.safeProductId(it)) }
        assertTrue(ResourcePolicy.safeProductId("9d57-12345"))
    }
}
