package me.purainity.miuithemeextension.hook

import android.content.Context
import me.purainity.miuithemeextension.config.Setting

object WidgetHooks {
    fun maml(hooks: Hooks) = hooks.constant("com.miui.maml.widget.edit.MamlutilKt",
        "themeManagerSupportPaidWidget", false, Setting.WIDGETS, signature = {
            it.parameterTypes.contentEquals(arrayOf(Context::class.java))
        })
    fun assistant(hooks: Hooks) {
        maml(hooks)
        val prefix = "com.miui.personalassistant.picker.business.detail"
        listOf("PickerDetailResponse", "PickerDetailResponseWrapper").forEach { bean ->
            hooks.constant("$prefix.bean.$bean", "isPay", false, Setting.WIDGETS)
            hooks.constant("$prefix.bean.$bean", "isBought", true, Setting.WIDGETS)
        }
        mapOf("isCanDirectAddMaMl" to true, "shouldCheckMamlBoughtState" to false,
            "isTargetPositionMamlPayAndDownloading" to false, "checkIsIndependentProcessWidgetForPosition" to true).forEach { (name, value) ->
            hooks.constant("$prefix.PickerDetailViewModel", name, value, Setting.WIDGETS,
                after = name == "isCanDirectAddMaMl", signature = { it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) })
        }
        hooks.constant("$prefix.utils.PickerDetailDownloadManager\$Companion", "isCanDownload", true, Setting.WIDGETS,
            signature = { it.parameterTypes.size == 1 && it.parameterTypes[0].name == "$prefix.bean.PickerDetailResponse" })
        hooks.constant("$prefix.utils.PickerDetailUtil", "isCanAutoDownloadMaMl", true, Setting.WIDGETS)
        hooks.constant("com.miui.personalassistant.picker.bean.cards.SuitEntity", "isShowPayLogo", false, Setting.WIDGET_BADGES, after = true)
    }
    fun home(hooks: Hooks) {
        maml(hooks)
        hooks.constant("com.miui.home.launcher.gadget.MaMlPendingHostView", "isCanAutoStartDownload", true, Setting.WIDGETS)
    }
}
