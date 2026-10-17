package me.purainity.miuithemeextension.hook

import android.content.Context
import me.purainity.miuithemeextension.config.*

/** system_server 内的框架层 Hook（6.2）：DRM 瞬时窗口按调用路径租借放行，外加广告开关改写 */
class SystemHooks(private val hooks: Hooks) {
    private val client = hooks.client
    // DRM 瞬时窗口（8.3）：按线程进入租借，未租借线程读到真实校验结果
    private val window = ScopedHookWindow<ResourceKind>({ installWindow() }) { error -> HookLog.error("DRM window", error) }
    // 窗口租借的类别 → 开关映射（6.2 表注）
    private fun enabled(kind: ResourceKind): Boolean = when (kind) {
        // 普通资源（含本地导入主题与付费主题）的框架层 DRM 放行统一由第三方主题开关承载
        // 解锁付费主题资源从属于该开关，故此处单挂即可同时覆盖两者
        ResourceKind.STANDARD -> client.config.enabled(Setting.THIRD_THEME)
        ResourceKind.LARGE_ICON -> client.config.enabled(Setting.LARGE_ICONS)
        ResourceKind.MAML -> client.config.enabled(Setting.WIDGETS)
        ResourceKind.UNSUPPORTED -> false
    }
    /** 按主题目录路径判定资源类别（8.1 路径版）：大图标段、MAML 关键词段或普通资源 */
    private fun contentKind(path: String): ResourceKind {
        if (!path.startsWith("/data/system/theme/")) return ResourceKind.UNSUPPORTED
        val parts = path.removePrefix("/data/system/theme/").split('/')
        if ("large_icons" in parts || "largeicons" in parts) return ResourceKind.LARGE_ICON
        if (parts.any { ResourcePolicy.kind(it) == ResourceKind.MAML }) return ResourceKind.MAML
        return ResourceKind.STANDARD
    }
    /** 安装 system_server 内的全部注入点（6.2） */
    fun install() {
        // createSystemContext 先于 startBootstrapServices 执行，before 阶段 mSystemContext 已可用（6.2 #1）
        // 此时 ContentService 尚未启动，这次异步尝试预期失败，真正的拉取发生在 validateTheme 入口（8.6）
        hooks.methods("com.android.server.SystemServer") { it.name == "startBootstrapServices" }.forEach { method ->
            hooks.install(method, before = { param ->
                param.thisObject?.let { systemServer ->
                    (Hooks.read(systemServer, "mSystemContext") as? Context)?.let { client.connect(it) }
                }
            })
        }
        // 备用路径：经 getSystemContext 连接配置客户端（6.2 #2）
        hooks.methods("android.app.ActivityThread") { it.name == "systemMain" && it.parameterTypes.size == 0 }.forEach { method ->
            hooks.install(method, after = { param ->
                val activityThread = param.result
                if (!param.hasThrowable() && activityThread != null) {
                    (Hooks.call(activityThread, "getSystemContext") as? Context)?.let { client.connect(it) }
                }
            })
        }
        // validateTheme 的两种历史签名（6.2 #3、9）：新版四参 void 与旧版三参 boolean
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
                // 同步拉取配置（eager 连接）：system_server 早期注入点在 ContentService 启动前必然失败
                // validateTheme 只发生在系统就绪后，此处同步重拉可让本帧窗口的租借判定读到真实配置（8.6）
                // 失败则全关放行真实校验
                client.connect(param.args[0] as Context, eager = true)
                val kind = contentKind(param.args[1] as? String ?: "")
                param.setObjectExtra("mte.drm", window.enter(kind, enabled(kind)))
            }, after = { param ->
                @Suppress("UNCHECKED_CAST")
                val frame = param.getObjectExtra("mte.drm") as? ScopedHookWindow.Frame<ResourceKind>
                if (frame != null) window.exit(frame)
            })
        }
        // 免费主题的广告变现框架（7.1）：支持判定置否、设置跳过原方法（6.2 #5–#6）
        hooks.constant("miui.drm.DrmManager", "isSupportAd", false, Setting.ADS,
            signature = { it.parameterTypes.size == 1 && (it.parameterTypes[0] == Context::class.java || it.parameterTypes[0] == java.io.File::class.java) })
        hooks.methods("miui.drm.DrmManager") { it.name == "setSupportAd" && it.returnType == Void.TYPE &&
            it.parameterTypes.contentEquals(arrayOf(Context::class.java, Boolean::class.javaPrimitiveType)) }.forEach { method ->
            hooks.install(method, before = { param ->
                if (client.config.enabled(Setting.ADS)) param.result = null
            })
        }
    }
    /** 窗口租借时装入的 DRM 放行 Hook 集（6.2 #4）；任一安装失败则回滚已装项后上抛（8.3） */
    private fun installWindow(): List<() -> Unit> {
        val targets = hooks.methods("miui.drm.DrmManager") { method ->
            (method.name == "isLegal" && method.returnType.name == $$"miui.drm.DrmManager$DrmResult") ||
                (method.name in setOf("isPermanentRights", "isRightsFileLegal") &&
                    method.returnType == Boolean::class.javaPrimitiveType)
        }
        check(targets.isNotEmpty()) { "No DRM targets" }
        val undo = ArrayList<() -> Unit>()
        try {
            targets.forEach { method ->
                // isLegal 注入真实 DRM_SUCCESS 枚举（8.3），布尔方法注入 true
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
