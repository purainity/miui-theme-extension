package me.purainity.miuithemeextension.hook

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import me.purainity.miuithemeextension.config.Setting
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** 小组件相关 Hook：智能助理与系统桌面分别装配（6.3、6.4） */
object WidgetHooks {
    /** MAML SDK 付费支持门禁置否（6.1 #13），智能助理与系统桌面共用 */
    fun maml(hooks: Hooks) = hooks.constant("com.miui.maml.widget.edit.MamlutilKt",
        "themeManagerSupportPaidWidget", false, Setting.WIDGETS, signature = {
            it.parameterTypes.contentEquals(arrayOf(Context::class.java))
        })
    /** 智能助理（6.3）：放行付费小组件的详情判定与下载，隐藏付费角标 */
    fun assistant(hooks: Hooks) {
        hooks.group("maml", optional = true) { maml(hooks) }
        hooks.group("widget_access") {
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
            // 下载许可放行（#7–#8）
            hooks.constant($$"$$prefix.utils.PickerDetailDownloadManager$Companion", "isCanDownload", true, Setting.WIDGETS,
                signature = { it.parameterTypes.size == 1 && it.parameterTypes[0].name == "$prefix.bean.PickerDetailResponse" })
            hooks.constant("$prefix.utils.PickerDetailUtil", "isCanAutoDownloadMaMl", true, Setting.WIDGETS)
        }
        hooks.group("widget_badges") {
            // 套装卡片角标（6.3 #12）
            hooks.constant("com.miui.personalassistant.picker.bean.cards.SuitEntity", "isShowPayLogo", false, Setting.WIDGET_BADGES, after = true)
            regularWidgetBadges(hooks)
        }
    }
    /** 精选、分类等普通小组件卡片只隐藏角标视图，不改写付费状态（6.3 #13） */
    @SuppressLint("DiscouragedApi")
    private fun regularWidgetBadges(hooks: Hooks) {
        val ids = AtomicReference<Set<Int>>(emptySet())
        val visibility = WidgetBadgeVisibility()
        hooks.install(View::class.java.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType), before = { call ->
            val view = call.thisObject as? ImageView ?: return@install
            val id = view.id
            if (id !in ids.get()) return@install
            val owner = try { view.resources.getResourcePackageName(id) } catch (_: Resources.NotFoundException) { return@install }
            if (owner != "com.miui.personalassistant") return@install
            val requested = call.args[0] as Int
            call.args[0] = visibility.intercept(view, requested, hooks.client.config.enabled(Setting.WIDGET_BADGES))
        }) ?: return
        hooks.client.onAttached.add { context ->
            // 宿主资源 ID 不属于模块且会随版本变化，只能按名称解析
            val matched = listOf("iv_pay_logo", "iv_pay_logo_with_add").mapNotNull { name ->
                context.resources.getIdentifier(name, "id", context.packageName).takeIf { it != 0 }
            }.toSet()
            ids.set(matched)
            hooks.log.info("Widget badge view IDs: matched=${matched.size}/2")
        }
        val hidden = AtomicBoolean(false)
        val main = Handler(Looper.getMainLooper())
        hooks.client.listeners.add { config ->
            val enabled = config.enabled(Setting.WIDGET_BADGES)
            if (hidden.getAndSet(enabled) != enabled) main.post {
                visibility.snapshot().forEach { (view, requested) ->
                    try { (view as ImageView).visibility = requested } catch (error: Throwable) {
                        if (error is VirtualMachineError || error is ThreadDeath) throw error
                        hooks.log.error("widget_badge_visibility", "refresh", error)
                    }
                }
            }
        }
    }
    /** 系统桌面（6.4）：放行 MAML 自动开始下载 */
    fun home(hooks: Hooks) {
        hooks.group("maml", optional = true) { maml(hooks) }
        hooks.group("widget_access") {
            hooks.constant("com.miui.home.launcher.gadget.MaMlPendingHostView", "isCanAutoStartDownload", true, Setting.WIDGETS)
        }
    }
}
