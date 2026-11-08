package me.purainity.miuithemeextension

import me.purainity.miuithemeextension.dex.DexIndex
import org.junit.Assert.assertThrows
import org.junit.Test

class DexIndexTest {
    @Test fun rejectsMalformedHeaders() {
        listOf(ByteArray(0), ByteArray(112), byteArrayOf(100, 101, 120, 10)).forEach { bytes ->
            assertThrows(IllegalArgumentException::class.java) { DexIndex(bytes).methods("Lcom/") }
        }
    }
}
