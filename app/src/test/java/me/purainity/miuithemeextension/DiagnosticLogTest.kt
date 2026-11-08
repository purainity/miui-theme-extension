package me.purainity.miuithemeextension

import me.purainity.miuithemeextension.hook.ConfigLogState
import me.purainity.miuithemeextension.hook.ConfigLogState.Event
import me.purainity.miuithemeextension.hook.DiagnosticLog
import me.purainity.miuithemeextension.hook.DiscoveryStats
import me.purainity.miuithemeextension.hook.HookGroupStats
import me.purainity.miuithemeextension.hook.HostLog
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DiagnosticLogTest {
    @Test fun prefixesAndErrorKeysIncludeHostPointPhaseAndType() {
        val messages = mutableListOf<String>()
        val log = DiagnosticLog(messages::add)
        log.info("com.android.thememanager", "Module loaded")
        log.warn("android", "Unavailable")
        log.error("com.android.thememanager", "point", "before", IllegalStateException("private"))
        log.error("com.android.thememanager", "point", "before", IllegalStateException("other"))
        log.error("com.android.thememanager", "point", "after", IllegalStateException())
        log.error("com.miui.home", "point", "before", IllegalStateException())
        log.error("com.android.thememanager", "other", "before", IllegalStateException())
        log.error("com.android.thememanager", "point", "before", IllegalArgumentException())

        assertEquals(7, messages.size)
        assertEquals("[MIUIThemeExtension][I][com.android.thememanager] Module loaded", messages[0])
        assertEquals("[MIUIThemeExtension][W][android] Unavailable", messages[1])
        assertTrue(messages[2].startsWith("[MIUIThemeExtension][E][com.android.thememanager] Failure: phase=before point=point type=java.lang.IllegalStateException"))
    }

    @Test fun boundLogsKeepCompletePackageNames() {
        val hosts = listOf(
            "com.android.thememanager", "com.miui.personalassistant", "com.miui.home",
            "android", "me.purainity.miuithemeextension"
        )
        val messages = mutableListOf<String>()
        val log = DiagnosticLog(messages::add)
        hosts.forEach { host -> HostLog(log, host).info("Module loaded") }
        assertEquals(hosts.map { "[MIUIThemeExtension][I][$it] Module loaded" }, messages)
    }

    @Test fun duplicateErrorsAreThreadSafeAndCapacityIsBounded() {
        val messages = Collections.synchronizedList(mutableListOf<String>())
        val log = DiagnosticLog(messages::add, maxErrors = 2)
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val done = CountDownLatch(64)
        repeat(64) {
            executor.execute {
                try { start.await(); log.error("com.android.thememanager", "same", "before", IllegalStateException()) }
                finally { done.countDown() }
            }
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        executor.shutdownNow()
        log.error("com.miui.home", "another", "after", IllegalStateException())
        repeat(3) { log.error("android", "overflow$it", "install", IllegalStateException()) }
        assertEquals(3, messages.size)
        assertTrue(messages.last().contains("Additional distinct errors suppressed: limit=2"))
    }

    @Test fun stackTraceOmitsMessagesAndPathsAndLimitsFramesAndCauses() {
        val messages = mutableListOf<String>()
        val log = DiagnosticLog(messages::add)
        val cause = IllegalArgumentException("cause_secret_product")
        cause.stackTrace = arrayOf(StackTraceElement("CauseClass", "call", "/secret/cause.kt", 17))
        val error = IllegalStateException("message_secret_path", cause)
        error.stackTrace = arrayOf(StackTraceElement("ExampleClass", "call", "/secret/file.kt", 42))
        log.error("com.android.thememanager", "point", "before", error)
        val result = messages.single()
        assertTrue(result.contains("ExampleClass.call:42"))
        assertTrue(result.contains("Caused by: java.lang.IllegalArgumentException"))
        assertFalse(result.contains("secret"))
        assertFalse(result.contains(".kt"))

        val many = IllegalStateException()
        many.stackTrace = Array(100) { StackTraceElement("Class$it", "call", "ignored", it) }
        log.error("com.android.thememanager", "many", "after", many)
        assertEquals(24, messages.last().lines().count { it.startsWith("    at ") })
        assertTrue(messages.last().contains("... truncated"))
        val long = IllegalStateException()
        long.stackTrace = Array(24) { StackTraceElement("C".repeat(500), "method".repeat(100), "private", it) }
        log.error("com.android.thememanager", "long", "after", long)
        assertTrue(messages.last().substringAfter("type=java.lang.IllegalStateException").length <= 2400)
    }

    @Test fun brokenThrowableFormattingDoesNotAffectHost() {
        val messages = mutableListOf<String>()
        val error = object : RuntimeException() {
            override fun getStackTrace(): Array<StackTraceElement> = throw IllegalStateException("private")
        }
        DiagnosticLog(messages::add).error("com.android.thememanager", "broken", "after", error)
        assertTrue(messages.single().contains("... trace unavailable"))
        assertFalse(messages.single().contains("private"))
        val fatal = object : RuntimeException() {
            override fun getStackTrace(): Array<StackTraceElement> = throw OutOfMemoryError()
        }
        assertThrows(OutOfMemoryError::class.java) {
            DiagnosticLog(messages::add).error("com.android.thememanager", "fatal", "after", fatal)
        }
    }

    @Test fun cyclicCauseChainIsLimited() {
        val first = IllegalStateException("private")
        val second = IllegalArgumentException("private")
        first.initCause(second)
        second.initCause(first)
        first.stackTrace = emptyArray()
        second.stackTrace = emptyArray()
        val messages = mutableListOf<String>()
        DiagnosticLog(messages::add).error("com.android.thememanager", "cycle", "after", first)
        assertTrue(messages.single().contains("... truncated"))
        assertEquals(1, messages.single().split("Caused by:").size - 1)
    }

    @Test fun sinkFailuresAreIsolatedButFatalErrorsAreRethrown() {
        val log = DiagnosticLog(sink = { throw IllegalStateException() })
        log.info("com.android.thememanager", "test")
        log.error("com.android.thememanager", "point", "before", IllegalStateException())
        val vm = OutOfMemoryError()
        assertSame(vm, assertThrows(OutOfMemoryError::class.java) { log.error("com.android.thememanager", "point", "before", vm) })
        val death = ThreadDeath()
        assertSame(death, assertThrows(ThreadDeath::class.java) { log.error("com.android.thememanager", "point", "before", death) })
        val sinkVm = DiagnosticLog(sink = { throw vm })
        assertSame(vm, assertThrows(OutOfMemoryError::class.java) { sinkVm.info("com.android.thememanager", "test") })
    }

    @Test fun configStatesReportEarlyFailureReadyFailureRecoveryAndRepeat() {
        val state = ConfigLogState(systemHost = true)
        assertEquals(Event.EARLY_FAILURE, state.failure(ready = false))
        assertEquals(Event.NONE, state.failure(ready = false))
        assertEquals(Event.FAILURE, state.failure(ready = true))
        assertEquals(Event.NONE, state.failure(ready = true))
        assertEquals(Event.RECOVERED, state.success())
        assertEquals(Event.NONE, state.success())
        assertEquals(Event.FAILURE, state.failure(ready = true))
        assertEquals(Event.RECOVERED, state.success())
        val app = ConfigLogState(systemHost = false)
        assertEquals(Event.LOADED, app.success())
        assertEquals(Event.NONE, app.success())
        assertEquals(Event.FAILURE, app.failure(ready = false))
    }

    @Test fun groupStatesDistinguishOptionalRequiredAndPartialInstallation() {
        val required = HookGroupStats("validation")
        assertEquals("not_found", required.state())
        assertTrue(required.unavailable())
        val optional = HookGroupStats("maml", optional = true)
        assertEquals("optional_not_found", optional.state())
        assertFalse(optional.unavailable())
        required.installed++
        assertEquals("installed(1)", required.state())
        assertFalse(required.unavailable())
        required.lookupFailed = true
        required.failed++
        assertEquals("partial(installed=1,failed=1,lookup_failed=true)", required.state())
        required.installed = 0
        assertEquals("failed(installed=0,failed=1,lookup_failed=true)", required.state())
        assertTrue(required.unavailable())
        required.aborted = true
        assertEquals("aborted(installed=0)", required.state())
    }

    @Test fun discoverySeparatesCandidateResolutionAndInstallation() {
        val stats = DiscoveryStats("cache")
        stats.drm.candidates = 3
        stats.drm.resolved = 2
        stats.drm.installed = 1
        stats.large.candidates = 2
        stats.large.resolved = 1
        stats.large.installed = 1
        assertEquals("DEX discovery: source=cache candidates/resolved/installed drm=3/2/1 large_icon=2/1/1 scan_errors=0", stats.summary())
        assertFalse(stats.hasIssues())
        stats.scanErrors++
        assertTrue(stats.hasIssues())
        stats.failedPhase = "resolve"
        assertTrue(stats.summary().contains("failed_phase=resolve"))
        stats.scanErrors = 0
        stats.failedPhase = null
        stats.large.installed = 0
        assertTrue(stats.hasIssues())
    }
}
