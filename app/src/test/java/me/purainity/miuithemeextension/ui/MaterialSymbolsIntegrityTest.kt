package me.purainity.miuithemeextension.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class MaterialSymbolsIntegrityTest {
    @Test
    fun listedIconsMatchOfficialAssets() {
        // 校验 Google 官方 Material Symbols Outlined 24px 的原始资源
        // 以下图标来自 google/material-design-icons 的 10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7
        val expected = mapOf(
            "ic_power_settings_new" to "f49ac857be602c5beb1183196da1d69ac782ad97491573fe705e6e5b99318f27",
            "ic_palette" to "77da392d239862f1c1a4f736d698debcffe9477eacb49947b41210360af23b92",
            "ic_lock_open" to "fa5fbc5e02a485a5b62fc869a0fc50b0616d5481e807aef1ee1f87de5a1ccd55",
            "ic_money_off" to "e2b6aac3d1eca3d5bac111a664899c86b3041d3377038c511f58b18f7ac700f6",
            "ic_apps" to "6d16e921a41841590351fef37fb40fcba3b92a8846699ec6ab52d23e74e33fb3",
            "ic_widgets" to "eae222b4d2dc51a2a83b5467928a174493a49d52d2276cbb4f0320ecef27ceb3",
            "ic_ad_units" to "eaab5a5ff077ab994b2e8ac08127a97451a2ddf51d8389381327c5561867ec56",
            "ic_visibility_off" to "24c67489b46f0bae2062ef24e0334ae3252f5d45fadff1ecfaedec43777730b7",
            "ic_back" to "cd1f5a1109c07c79ac3e52a9a1ae8ab14be7a3bff7f7bc3559f7088d60eca3aa",
            "ic_chevron" to "d3f53b271fdb2150775b4252bf46139c3ab5ca872802372a1089e6e1dd6f2a35",
            "ic_code" to "fcfc955faa74c73a35e210fe5b6d7e0e074e391eb019fd6fb1d3a72428b9eee3",
            "ic_external" to "12b1dfc1be3b858a2d22781dcae341ec180834266198022b2f63494f7aab9c04",
            "ic_info" to "b105df7f824e7eafbd0a73ebc55085134409161d606894c97685ba1cd38cbcc2",
            "ic_person" to "ab409acf8825d92c92928e06d2d84c003f9e227033594443d0ba5700fe2fe072",
        )
        // 只保留既有清单的校验，不要求每个新增图标都加入完整性清单
        expected.forEach { (name, expectedDigest) ->
            val icon = File("src/main/res/drawable/$name.xml")
            val digest = MessageDigest.getInstance("SHA-256").digest(icon.readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            assertEquals("图标与官方资源不一致：${icon.name}", expectedDigest, digest)
        }
    }
}
