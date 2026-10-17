package me.purainity.miuithemeextension.hook

import android.content.Context
import me.purainity.miuithemeextension.config.*
import java.lang.reflect.Method

class SystemHooks(private val hooks: Hooks) {
    private val client = hooks.client
    private val window = ScopedHookWindow<ResourceKind>({ installWindow() }) { error -> HookLog.error("DRM window", error) }
    private fun enabled(kind: ResourceKind): Boolean = kind != ResourceKind.UNSUPPORTED && client.config.enabled(when (kind) {
        // 普通资源（含本地导入主题与付费主题）的框架层 DRM 放行统一由第三方主题开关承载；
        // 解锁付费主题资源从属于该开关，故此处单挂即可同时覆盖两者
        ResourceKind.STANDARD -> Setting.THIRD_THEME
        ResourceKind.LARGE_ICON -> Setting.LARGE_ICONS
        ResourceKind.MAML -> Setting.WIDGETS
        ResourceKind.UNSUPPORTED -> Setting.MASTER
    })
    private fun contentKind(path: String): ResourceKind {
        if (!path.startsWith("/data/system/theme/")) return ResourceKind.UNSUPPORTED
        val parts = path.removePrefix("/data/system/theme/").split('/')
        if ("large_icons" in parts || "largeicons" in parts) return ResourceKind.LARGE_ICON
        if (parts.any { ResourcePolicy.kind(it) == ResourceKind.MAML }) return ResourceKind.MAML
        return ResourceKind.STANDARD
    }
    fun install() {
        // 在 SystemServer 创建系统上下文之后读取；重试由配置线程负责
        hooks.methods("com.android.server.SystemServer") { it.name == "startBootstrapServices" }.forEach { method ->
            hooks.install(method, before = { param ->
                param.thisObject?.let { systemServer ->
                    (Hooks.read(systemServer, "mSystemContext") as? Context)?.let { client.connect(it) }
                }
            })
        }
        hooks.methods("android.app.ActivityThread") { it.name == "systemMain" && it.parameterTypes.size == 0 }.forEach { method ->
            hooks.install(method, after = { param ->
                val activityThread = param.result
                if (!param.hasThrowable() && activityThread != null) {
                    (Hooks.call(activityThread, "getSystemContext") as? Context)?.let { client.connect(it) }
                }
            })
        }
        val validate = hooks.methods("miui.drm.ThemeReceiver") { method ->
            method.name == "validateTheme" && when (method.parameterTypes.size) {
                4 -> method.returnType == Void.TYPE && method.parameterTypes[0] == Context::class.java &&
                    method.parameterTypes[1] == String::class.java && method.parameterTypes[2] == String::class.java &&
                    Map::class.java.isAssignableFrom(method.parameterTypes[3])
                3 -> method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes[0] == Context::class.java &&
                    method.parameterTypes[1] == String::class.java && method.parameterTypes[2] == String::class.java
                else -> false
            }
        }
        validate.forEach { method ->
            hooks.install(method, before = { param ->
                // 同步拉取配置（eager 连接）：system_server 早期两个注入点的连接尝试在 ContentService
                // 启动前必然失败，validateTheme 只发生在系统就绪后，此处同步重拉可让本帧窗口的
                // 租借判定读到真实配置（见 8.6 system_server 读取时机）；失败则全关放行真实校验
                client.connect(param.args[0] as Context, eager = true)
                val kind = contentKind(param.args[1] as? String ?: "")
                param.setObjectExtra("mte.drm", window.enter(kind, enabled(kind)))
            }, after = { param ->
                @Suppress("UNCHECKED_CAST")
                val frame = param.getObjectExtra("mte.drm") as? ScopedHookWindow.Frame<ResourceKind>
                if (frame != null) window.exit(frame)
            })
        }
        hooks.constant("miui.drm.DrmManager", "isSupportAd", false, Setting.ADS,
            signature = { it.parameterTypes.size == 1 && (it.parameterTypes[0] == Context::class.java || it.parameterTypes[0] == java.io.File::class.java) })
        hooks.methods("miui.drm.DrmManager") { it.name == "setSupportAd" && it.returnType == Void.TYPE &&
            it.parameterTypes.contentEquals(arrayOf(Context::class.java, Boolean::class.javaPrimitiveType)) }.forEach { method ->
            hooks.install(method, before = { param ->
                if (client.config.enabled(Setting.ADS)) param.result = null
            })
        }
    }
    private fun installWindow(): List<() -> Unit> {
        val targets = hooks.methods("miui.drm.DrmManager") { method ->
            method.name == "isLegal" && method.returnType.name == "miui.drm.DrmManager\$DrmResult" ||
                method.name in setOf("isPermanentRights", "isRightsFileLegal") && method.returnType == Boolean::class.javaPrimitiveType
        }
        check(targets.isNotEmpty()) { "No DRM targets" }
        val undo = ArrayList<() -> Unit>()
        try {
            targets.forEach { method ->
                val result = if (method.returnType == Boolean::class.javaPrimitiveType) true else Hooks.drmSuccess(method)
                val unhook = hooks.install(method, before = { param ->
                    val current = window.current()
                    if (current != null && enabled(current)) param.result = result
                }) ?: error("DRM hook installation failed")
                undo += { unhook.unhook() }
            }
            return undo
        } catch (e: Exception) {
            undo.asReversed().forEach { remove -> try { remove() } catch (cleanup: Exception) { HookLog.error("DRM rollback", cleanup) } }
            throw e
        }
    }
}
