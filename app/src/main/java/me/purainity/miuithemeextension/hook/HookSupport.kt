package me.purainity.miuithemeextension.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import me.purainity.miuithemeextension.config.Setting
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Xposed 日志：统一前缀，同一位置同一类异常只记录首次，避免刷屏（1） */
object HookLog {
    private val seen = ConcurrentHashMap.newKeySet<String>()
    fun info(message: String) = XposedBridge.log("MIUI 主题扩展: $message")
    fun error(point: String, error: Throwable) {
        if (seen.add("$point:${error.javaClass.name}")) info("$point: ${error.javaClass.simpleName} ${error.message.orEmpty().take(160)}")
    }
}

/** Hook 装配基础（5.2 容错）：类查找、方法过滤、安装与异常隔离，任何失败只记日志不上抛 */
class Hooks(val loader: ClassLoader, val client: HostConfigClient) {
    /** 按全限定名加载宿主类，找不到返回 null */
    fun find(name: String): Class<*>? = try { Class.forName(name, false, loader) }
        catch (e: ClassNotFoundException) { HookLog.error(name, e); null }
        catch (e: LinkageError) { HookLog.error(name, e); null }
    /** 取类中通过过滤的已声明方法，异常时返回空列表 */
    fun methods(name: String, filter: (Method) -> Boolean): List<Method> = try {
        find(name)?.declaredMethods?.filter(filter).orEmpty()
    } catch (e: Throwable) { isolate(name, e); emptyList() }
    /** 安装方法 Hook，回调内异常与安装失败都只记日志；失败返回 null */
    fun install(member: Member, before: ((XC_MethodHook.MethodHookParam) -> Unit)? = null,
                after: ((XC_MethodHook.MethodHookParam) -> Unit)? = null): XC_MethodHook.Unhook? = try {
        val unhook = XposedBridge.hookMethod(member, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) { runCallback(member, before, param) }
            override fun afterHookedMethod(param: MethodHookParam) { runCallback(member, after, param) }
        })
        unhook
    } catch (e: Throwable) { isolate(member.toString(), e); null }
    // 回调异常不外泄到宿主调用栈
    private fun runCallback(member: Member, callback: ((XC_MethodHook.MethodHookParam) -> Unit)?, param: XC_MethodHook.MethodHookParam) {
        if (callback == null) return
        try { callback(param) } catch (e: Throwable) { isolate(member.toString(), e) }
    }
    // 虚拟机级致命错误原样上抛，其余降级为日志
    private fun isolate(point: String, error: Throwable) {
        if (error is VirtualMachineError || error is ThreadDeath) throw error
        HookLog.error(point, error)
    }
    /** 常量改写：开关开启且原方法无异常时把布尔返回值固定为 value，同名重载按 signature 区分 */
    fun constant(name: String, method: String, value: Boolean, setting: Setting, after: Boolean = false,
                 signature: ((Method) -> Boolean)? = null) {
        val candidates = methods(name) { it.name == method && it.returnType == Boolean::class.javaPrimitiveType &&
            (signature?.invoke(it) ?: (it.parameterTypes.size == 0)) }
        if (candidates.isEmpty()) return
        candidates.forEach { target ->
            val callback: (XC_MethodHook.MethodHookParam) -> Unit = { param ->
                if (client.config.enabled(setting) && !param.hasThrowable()) param.result = value
            }
            // after=true 在原方法执行后改写返回值：before 改写会跳过原方法体，需保留宿主副作用时用 after
            install(target, before = if (after) null else callback, after = if (after) callback else null)
        }
    }
    companion object {
        // 沿继承链收集全部已声明字段
        fun fields(type: Class<*>): Sequence<Field> = generateSequence(type) { it.superclass }.flatMap { it.declaredFields.asSequence() }
        /** 读实例字段，找不到返回 null */
        fun read(obj: Any, name: String): Any? {
            val field = fields(obj.javaClass).firstOrNull { it.name == name } ?: return null
            field.isAccessible = true
            return field.get(obj)
        }
        /** 写实例字段，字段不存在返回 false */
        fun write(obj: Any, name: String, value: Any): Boolean {
            val field = fields(obj.javaClass).firstOrNull { it.name == name } ?: return false
            field.isAccessible = true
            field.set(obj, value)
            return true
        }
        /** 调用无参方法 */
        fun call(obj: Any, name: String): Any? = obj.javaClass.getMethod(name).invoke(obj)
        /** 从被 Hook 方法的真实返回类型反射取得 DRM_SUCCESS（8.3），保证枚举实例来自宿主进程实际加载的框架类 */
        fun drmSuccess(method: Method): Any = requireNotNull(method.returnType.getField("DRM_SUCCESS").get(null))
        /** Java 类型转 JVM 类型描述符 */
        fun descriptor(type: Class<*>): String = when {
            type.isArray -> type.name.replace('.', '/')
            !type.isPrimitive -> "L${type.name.replace('.', '/')};"
            else -> mapOf("void" to "V", "boolean" to "Z", "byte" to "B", "char" to "C", "short" to "S", "int" to "I", "long" to "J", "float" to "F", "double" to "D").getValue(type.name)
        }
    }
}
