package me.purainity.miuithemeextension

import me.purainity.miuithemeextension.hook.*
import me.purainity.miuithemeextension.config.ModuleConfig
import me.purainity.miuithemeextension.config.ResourceKind
import me.purainity.miuithemeextension.config.Setting
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RuntimeSafetyTest {
    @Test fun journalPreservesExistingAndPopulatedFilesAcrossRestart() {
        val root = Files.createTempDirectory("mte-rights").toFile()
        try {
            val folder = File(root, "rights").apply { mkdirs() }
            val journal = File(root, "journal")
            val original = File(folder, "original-largeicons.mra").apply { writeText("") }
            val files = OwnedRightsFiles(folder, journal)
            assertFalse(files.prepare("original") { true })
            assertTrue(files.prepare("new") { true })
            assertTrue(files.prepare("filled") { true })
            File(folder, "filled-largeicons.mra").writeText("host rights")
            assertFalse(files.prepare("off") { false })
            OwnedRightsFiles(folder, journal).cleanup()
            assertTrue(original.exists())
            assertFalse(File(folder, "new-largeicons.mra").exists())
            assertEquals("host rights", File(folder, "filled-largeicons.mra").readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun windowsNestAndBlockDisabledInnerCalls() {
        var installs = 0; var removals = 0
        val windows = ScopedHookWindow<String>({ installs++; listOf({ removals++ }) }) { throw it }
        val outer = windows.enter("theme", true)
        val inner = windows.enter("large", false)
        assertNull(windows.current())
        windows.exit(inner)
        assertEquals("theme", windows.current())
        val nested = windows.enter("font", true)
        assertEquals(1, installs)
        windows.exit(nested)
        assertEquals(0, removals)
        windows.exit(outer)
        assertEquals(1, removals)
        assertNull(windows.current())
        windows.exit(outer)
        assertEquals(1, removals)
    }
    @Test fun windowsIsolateThreadsAndRetainOtherThreadsLease() {
        val installs = AtomicInteger(); val removals = AtomicInteger()
        val windows = ScopedHookWindow<String>({ installs.incrementAndGet(); listOf({ removals.incrementAndGet(); Unit }) }) { throw it }
        val ready = CountDownLatch(1); val proceed = CountDownLatch(1); val finished = CountDownLatch(1)
        val outer = windows.enter("theme", true)
        var workerError: Throwable? = null
        val worker = Thread {
            try {
                assertNull(windows.current())
                val frame = windows.enter("large", true)
                ready.countDown()
                check(proceed.await(5, TimeUnit.SECONDS))
                assertEquals("large", windows.current())
                assertEquals(0, removals.get())
                windows.exit(frame)
            } catch (error: Throwable) { workerError = error } finally { finished.countDown() }
        }
        worker.start()
        assertTrue(ready.await(5, TimeUnit.SECONDS))
        windows.exit(outer)
        assertEquals(0, removals.get())
        assertNull(windows.current())
        proceed.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        workerError?.let { throw it }
        assertEquals(1, installs.get()); assertEquals(1, removals.get())
    }
    @Test fun failedInstallNeverGrantsAWindow() {
        var failures = 0
        val windows = ScopedHookWindow<String>({ error("failed") }) { failures++ }
        val frame = windows.enter("theme", true)
        assertNull(windows.current())
        windows.exit(frame)
        assertEquals(1, failures)
    }
    @Test fun objectTagsUseIdentity() {
        data class Model(val id: Int)
        val first = Model(1); val equal = Model(1)
        val tags = WeakIdentityMap<String>()
        tags[first] = "large"
        assertEquals("large", tags[first])
        assertNull(tags[equal])
    }
    @Test fun cachedMutationsRestoreOnceWhenTheirGateTurnsOff() {
        val journal = ReversibleMutationJournal()
        val resource = Any()
        var restores = 0
        val on = ModuleConfig(values = mapOf(Setting.MASTER to true, Setting.RESOURCES to true))
        assertTrue(journal.record(resource, "resource-id", ResourceKind.STANDARD) { restores++ })
        assertFalse(journal.record(resource, "resource-id", ResourceKind.STANDARD) { restores++ })
        journal.restoreDisabled(on)
        assertEquals(0, restores)
        journal.restoreDisabled(ModuleConfig())
        assertEquals(1, restores)
        assertTrue(journal.record(resource, "resource-id", ResourceKind.STANDARD) { restores++ })
    }
}
