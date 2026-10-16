package me.purainity.miuithemeextension

import android.content.ContentValues
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.purainity.miuithemeextension.config.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsIntegrationTest {
    @Test fun providerSnapshotTracksCommittedPreferencesAndRejectsWrites() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ConfigStore(context)
        val old = store.read()
        try {
            assertTrue(store.write(Setting.MASTER, !old[Setting.MASTER]))
            context.contentResolver.query(ConfigContract.CONFIG_URI, null, null, null, null)!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(if (old[Setting.MASTER]) 0 else 1, cursor.getInt(cursor.getColumnIndexOrThrow(Setting.MASTER.key)))
                assertTrue(cursor.getLong(cursor.getColumnIndexOrThrow("revision")) > old.version)
            }
            assertThrows(SecurityException::class.java) { context.contentResolver.update(ConfigContract.CONFIG_URI, ContentValues(), null, null) }
            assertThrows(SecurityException::class.java) { context.contentResolver.insert(ConfigContract.CONFIG_URI, ContentValues()) }
        } finally { Setting.entries.forEach { store.write(it, old[it]) } }
    }
}
