package me.purainity.miuithemeextension.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import me.purainity.miuithemeextension.config.Setting
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object HookLog {
    private val seen = ConcurrentHashMap.newKeySet<String>()
    fun info(message: String) = XposedBridge.log("MIUI 主题扩展: $message")
    fun error(point: String, error: Throwable) {
        if (seen.add("$point:${error.javaClass.name}")) info("$point: ${error.javaClass.simpleName} ${error.message.orEmpty().take(160)}")
    }
}

class Hooks(val loader: ClassLoader, val client: HostConfigClient) {
    fun find(name: String): Class<*>? = try { Class.forName(name, false, loader) }
        catch (e: ClassNotFoundException) { HookLog.error(name, e); null }
        catch (e: LinkageError) { HookLog.error(name, e); null }
    fun methods(name: String, filter: (Method) -> Boolean): List<Method> = try {
        find(name)?.declaredMethods?.filter(filter).orEmpty()
    } catch (e: Throwable) { isolate(name, e); emptyList() }
    fun install(member: Member, before: ((XC_MethodHook.MethodHookParam) -> Unit)? = null,
                after: ((XC_MethodHook.MethodHookParam) -> Unit)? = null): XC_MethodHook.Unhook? = try {
        val unhook = XposedBridge.hookMethod(member, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) { runCallback(member, before, param) }
            override fun afterHookedMethod(param: MethodHookParam) { runCallback(member, after, param) }
        })
        unhook
    } catch (e: Throwable) { isolate(member.toString(), e); null }
    private fun runCallback(member: Member, callback: ((XC_MethodHook.MethodHookParam) -> Unit)?, param: XC_MethodHook.MethodHookParam) {
        if (callback == null) return
        try { callback(param) } catch (e: Throwable) { isolate(member.toString(), e) }
    }
    private fun isolate(point: String, error: Throwable) {
        if (error is VirtualMachineError || error is ThreadDeath) throw error
        HookLog.error(point, error)
    }
    fun constant(name: String, method: String, value: Boolean, setting: Setting, after: Boolean = false,
                 signature: ((Method) -> Boolean)? = null) {
        val candidates = methods(name) { it.name == method && it.returnType == Boolean::class.javaPrimitiveType &&
            (signature?.invoke(it) ?: it.parameterTypes.size == 0) }
        if (candidates.isEmpty()) return
        candidates.forEach { target ->
            val callback: (XC_MethodHook.MethodHookParam) -> Unit = { param ->
                if (client.config.enabled(setting) && !param.hasThrowable()) param.result = value
            }
            install(target, before = if (after) null else callback, after = if (after) callback else null)
        }
    }
    companion object {
        fun fields(type: Class<*>): Sequence<Field> = generateSequence(type) { it.superclass }.flatMap { it.declaredFields.asSequence() }
        fun read(obj: Any, name: String): Any? {
            val field = fields(obj.javaClass).firstOrNull { it.name == name } ?: return null
            field.isAccessible = true
            return field.get(obj)
        }
        fun write(obj: Any, name: String, value: Any): Boolean {
            val field = fields(obj.javaClass).firstOrNull { it.name == name } ?: return false
            field.isAccessible = true
            field.set(obj, value)
            return true
        }
        fun call(obj: Any, name: String): Any? = obj.javaClass.getMethod(name).invoke(obj)
        fun drmSuccess(method: Method): Any = requireNotNull(method.returnType.getField("DRM_SUCCESS").get(null))
        fun descriptor(type: Class<*>): String = when {
            type.isArray -> type.name.replace('.', '/')
            !type.isPrimitive -> "L${type.name.replace('.', '/')};"
            else -> mapOf("void" to "V", "boolean" to "Z", "byte" to "B", "char" to "C", "short" to "S", "int" to "I", "long" to "J", "float" to "F", "double" to "D").getValue(type.name)
        }
    }
}
