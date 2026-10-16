package me.purainity.miuithemeextension

import me.purainity.miuithemeextension.dex.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class DexIndexTest {
    @Test fun rejectsMalformedHeaders() {
        listOf(ByteArray(0), ByteArray(112), byteArrayOf(100, 101, 120, 10)).forEach { bytes ->
            assertThrows(IllegalArgumentException::class.java) { DexIndex(bytes).methods("Lcom/") }
        }
    }
    @Test fun locatesBusinessMethodsInBothThemeBaselines() {
        val root = System.getenv("MTE_HOST_FIXTURES")?.let(::File)
        assumeTrue("Set MTE_HOST_FIXTURES to the host APK directory", root?.isDirectory == true)
        for (name in listOf("MIUIThemeManager.apk", "主题壁纸_4.2.6.7.apk")) {
            val index = DexIndex.scanApk(File(root, name), "Lcom/android/thememanager/")
            val drm = index.filter(HostDiscovery::drm)
            val large = index.filter(HostDiscovery::largeIcon)
            assertEquals(name, 1, drm.size); assertEquals(name, 1, large.size)
            assertEquals("Lcom/android/thememanager/controller/online/zy;", drm.single().owner)
            assertEquals("Lcom/android/thememanager/util/s;", large.single().owner)
            assertEquals("toq", large.single().name)
            assertTrue(large.single().strings.contains("relativePackageList is empty"))
        }
    }
    @Test fun verifiesAllWidgetAndLauncherBaselines() {
        val root = System.getenv("MTE_HOST_FIXTURES")?.let(::File)
        assumeTrue(root?.isDirectory == true)
        for (name in listOf("MIUIPersonalAssistantT.apk", "智能助理_5.7.19-03251035.apk", "智能助理_5.7.20-09231741.apk")) {
            val index = DexIndex.scanApk(File(root, name), "Lcom/miui/personalassistant/picker/")
            assertTrue(name, index.any { it.owner.endsWith("/PickerDetailViewModel;") && it.name == "isCanDirectAddMaMl" && it.parameters == listOf("I") && it.returns == "Z" })
            assertTrue(name, index.any { it.owner.endsWith("/SuitEntity;") && it.name == "isShowPayLogo" && it.returns == "Z" })
        }
        for (name in listOf("MiuiHomeT.apk", "系统桌面_RELEASE-4.39.8.6095-05261641.apk")) {
            val index = DexIndex.scanApk(File(root, name), "Lcom/miui/home/launcher/gadget/")
            assertTrue(name, index.any { it.owner.endsWith("/MaMlPendingHostView;") && it.name == "isCanAutoStartDownload" && it.parameters.isEmpty() && it.returns == "Z" })
        }
    }
}
