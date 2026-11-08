package me.purainity.miuithemeextension.hook

import java.util.Collections
import java.util.IdentityHashMap

/** 与日志输出渠道解耦的诊断器，按宿主、位置、阶段与异常类型在进程内去重 */
class DiagnosticLog(private val sink: (String) -> Unit, private val maxErrors: Int = 256) {
    init { require(maxErrors >= 0) }
    private data class ErrorKey(val host: String, val point: String, val phase: String, val type: String)
    private val seen = HashSet<ErrorKey>()
    private var overflowReported = false

    /** 输出一次普通状态 */
    fun info(host: String, message: String) = emit(host, "I", message)

    /** 输出一次警告，调用方负责限制状态变化的频率 */
    fun warn(host: String, message: String) = emit(host, "W", message)

    /** 首次异常记录有限的堆栈与原因链，不记录异常消息和文件路径 */
    fun error(host: String, point: String, phase: String, error: Throwable) {
        if (error is VirtualMachineError || error is ThreadDeath) throw error
        val key = ErrorKey(host, point, phase, error.javaClass.name)
        val decision = synchronized(seen) {
            when {
                key in seen -> 0
                seen.size < maxErrors -> { seen += key; 1 }
                !overflowReported -> { overflowReported = true; 2 }
                else -> 0
            }
        }
        when (decision) {
            1 -> emit(host, "E", "Failure: phase=${safe(phase)} point=${safe(point)} type=${safe(error.javaClass.name)}${trace(error)}")
            2 -> warn(host, "Additional distinct errors suppressed: limit=$maxErrors")
        }
    }

    private fun emit(host: String, level: String, message: String) {
        try { sink("[MIUIThemeExtension][$level][${safe(host)}] $message") }
        catch (error: Throwable) {
            if (error is VirtualMachineError || error is ThreadDeath) throw error
        }
    }

    private fun safe(value: String): String = value.take(240).map { if (it.isISOControl()) '_' else it }.joinToString("")

    private fun trace(error: Throwable): String = try {
        formatTrace(error)
    } catch (failure: Throwable) {
        if (failure is VirtualMachineError || failure is ThreadDeath) throw failure
        "\n    ... trace unavailable"
    }

    private fun formatTrace(error: Throwable): String {
        val out = StringBuilder()
        val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var cause: Throwable? = error
        var causes = 0
        var frames = 0
        var truncated = false
        while (cause != null && causes < 3 && visited.add(cause)) {
            if (causes > 0) out.append("\nCaused by: ").append(safe(cause.javaClass.name))
            for (frame in cause.stackTrace) {
                if (frames >= 24 || out.length >= 2400) { truncated = true; break }
                out.append("\n    at ").append(safe(frame.className)).append('.').append(safe(frame.methodName))
                if (frame.lineNumber >= 0) out.append(':').append(frame.lineNumber)
                frames++
            }
            cause = cause.cause
            causes++
            if (truncated) break
        }
        if (cause != null || truncated) out.append("\n    ... truncated")
        return if (out.length <= 2400) out.toString() else out.substring(0, 2380) + "\n    ... truncated"
    }
}

/** 从配置读取失败到恢复的状态变化，重复失败不重复提示 */
class ConfigLogState(private val systemHost: Boolean) {
    enum class Event { EARLY_FAILURE, FAILURE, LOADED, RECOVERED, NONE }
    private var failed = false
    private var earlyReported = false
    private var failureReported = false
    private var loaded = false

    fun failure(ready: Boolean): Event {
        failed = true
        return if (systemHost && !ready) {
            if (earlyReported) Event.NONE else Event.EARLY_FAILURE.also { earlyReported = true }
        } else {
            if (failureReported) Event.NONE else Event.FAILURE.also { failureReported = true }
        }
    }

    fun success(): Event {
        val event = when {
            failed -> Event.RECOVERED
            !loaded -> Event.LOADED
            else -> Event.NONE
        }
        failed = false
        loaded = true
        earlyReported = false
        failureReported = false
        return event
    }
}

/** 同一功能组的挂载统计，候选缺失与实际安装失败分别表示 */
class HookGroupStats(val name: String, val optional: Boolean = false) {
    var installed = 0
    var failed = 0
    var lookupFailed = false
    var aborted = false

    fun state(): String = when {
        aborted -> "aborted(installed=$installed)"
        failed > 0 || lookupFailed -> {
            val status = if (installed > 0) "partial" else "failed"
            "$status(installed=$installed,failed=$failed,lookup_failed=$lookupFailed)"
        }
        installed > 0 -> "installed($installed)"
        optional -> "optional_not_found"
        else -> "not_found"
    }

    fun unavailable(): Boolean = !optional && installed == 0
}

/** DEX 发现结果分别记录候选、运行时回查和挂载数量 */
class DiscoveryStats(var source: String = "unknown") {
    class Counts(var candidates: Int = 0, var resolved: Int = 0, var installed: Int = 0) {
        override fun toString(): String = "$candidates/$resolved/$installed"
    }
    val drm = Counts()
    val large = Counts()
    var scanErrors = 0
    var failedPhase: String? = null

    fun summary(): String = "DEX discovery: source=$source candidates/resolved/installed " +
        "drm=$drm large_icon=$large scan_errors=$scanErrors" +
        (failedPhase?.let { " failed_phase=$it" } ?: "")

    fun hasIssues(): Boolean = failedPhase != null || scanErrors > 0 || drm.installed == 0 || large.installed == 0
}
