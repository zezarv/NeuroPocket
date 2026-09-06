package com.neuropocket.app.core

import org.junit.Assert.*
import org.junit.Test

class DlPollPolicyTest {
    @Test fun `active id is queried`() {
        assertTrue(DlPollPolicy.shouldQuery(done = false, failed = false, terminal = false))
    }
    @Test fun `done not queried`() {
        assertFalse(DlPollPolicy.shouldQuery(done = true, failed = false, terminal = false))
    }
    @Test fun `failed not re-queried but row kept`() {
        // строка остаётся в UI для dismiss, query прекращается
        assertFalse(DlPollPolicy.shouldQuery(done = false, failed = true, terminal = false))
    }
    @Test fun `terminal not re-queried`() {
        assertFalse(DlPollPolicy.shouldQuery(done = false, failed = true, terminal = true))
        assertFalse(DlPollPolicy.shouldQuery(done = false, failed = false, terminal = true))
    }
    @Test fun `terminal set ops`() {
        val s = DlPollPolicy.markTerminal(emptySet(), 42L)
        assertTrue(42L in s)
        assertFalse(42L in DlPollPolicy.unmarkTerminal(s, 42L))
    }
    @Test fun `missing row maps engine files to terminal event`() {
        assertEquals(
            EngineEvent.DOWNLOAD_FAILED,
            DlPollPolicy.engineEventForMissingRow("voice-engine-arm64.zip")
        )
        assertEquals(
            EngineEvent.DOWNLOAD_FAILED,
            DlPollPolicy.engineEventForMissingRow("libnpsd.so")
        )
    }
    @Test fun `missing row of plain model is ui-only`() {
        assertNull(DlPollPolicy.engineEventForMissingRow("Llama-3.2-3B-Instruct-Q4_K_M.gguf"))
        assertNull(DlPollPolicy.engineEventForMissingRow("NeuroPocket-update.apk"))
    }
}
