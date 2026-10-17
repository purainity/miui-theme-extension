package me.purainity.miuithemeextension.hook

import android.content.Context
import me.purainity.miuithemeextension.config.Setting

/** 小组件相关 Hook：智能助理与系统桌面分别装配（6.3、6.4） */
object WidgetHooks {
    /** MAML SDK 付费支持门禁置否（6.1 #13），智能助理与系统桌面共用 */
    fun maml(hooks: Hooks) = hooks.constant("com.miui.maml.widget.edit.MamlutilKt",
        "themeManagerSupportPaidWidget", false, Setting.WIDGETS, signature = {
            it.parameterTypes.contentEquals(arrayOf(Context::class.java))
        })
    /** 智能助理（6.3）：放行付费小组件的详情判定与下载，隐藏付费角标 */
    fun assistant(hooks: Hooks) {
        maml(hooks)
        val prefix = "com.miui.personalassistant.picker.business.detail"
        // 详情数据模型（#2–#5）：付费判定置否、已购判定置是，解除付费小组件的下载限制
        listOf("PickerDetailResponse", "PickerDetailResponseWrapper").forEach { bean ->
            hooks.constant("$prefix.bean.$bean", "isPay", false, Setting.WIDGETS)
            hooks.constant("$prefix.bean.$bean", "isBought", true, Setting.WIDGETS)
        }
        // 选择器判定（#6、#9–#11）：允许直添加、跳过购买态检查、下载中不视为付费、独立进程小组件放行
        mapOf("isCanDirectAddMaMl" to true, "shouldCheckMamlBoughtState" to false,
            "isTargetPositionMamlPayAndDownloading" to false, "checkIsIndependentProcessWidgetForPosition" to true).forEach { (name, value) ->
            hooks.constant("$prefix.PickerDetailViewModel", name, value, Setting.WIDGETS,
                after = name == "isCanDirectAddMaMl", signature = { it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) })
        }
        // 下载许可放行（#7–#8）与小组件卡片付费角标隐藏（#12）
        hooks.constant($$"$$prefix.utils.PickerDetailDownloadManager$Companion", "isCanDownload", true, Setting.WIDGETS,
            signature = { it.parameterTypes.size == 1 && it.parameterTypes[0].name == "$prefix.bean.PickerDetailResponse" })
        hooks.constant("$prefix.utils.PickerDetailUtil", "isCanAutoDownloadMaMl", true, Setting.WIDGETS)
        hooks.constant("com.miui.personalassistant.picker.bean.cards.SuitEntity", "isShowPayLogo", false, Setting.WIDGET_BADGES, after = true)
    }
    /** 系统桌面（6.4）：放行 MAML 自动开始下载 */
    fun home(hooks: Hooks) {
        maml(hooks)
        hooks.constant("com.miui.home.launcher.gadget.MaMlPendingHostView", "isCanAutoStartDownload", true, Setting.WIDGETS)
    }
}
