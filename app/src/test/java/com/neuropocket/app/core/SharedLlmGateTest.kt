package com.neuropocket.app.core

import org.junit.Assert.*
import org.junit.Test

class SharedLlmGateTest {
    @Test fun `idle allows everything`() {
        assertTrue(SharedLlmGate.canSend(false, false, false, false, false, false))
        assertTrue(SharedLlmGate.canSendPersona(false, false, false, false, false, false))
        assertTrue(SharedLlmGate.canRunAgent(false, false, false, false, false))
        assertTrue(SharedLlmGate.canAskNotes(false, false, false))
        assertTrue(SharedLlmGate.canLoadTextModel(false, false, false, false))
        assertTrue(SharedLlmGate.canStartHandsFree(false))
    }
    @Test fun `roundtable blocks shared llama`() {
        assertFalse(SharedLlmGate.canSend(false, false, false, false, false, true))
        assertFalse(SharedLlmGate.canSendPersona(false, false, false, false, false, true))
        assertFalse(SharedLlmGate.canRunAgent(false, false, false, false, true))
        assertFalse(SharedLlmGate.canAskNotes(false, false, true))
        assertFalse(SharedLlmGate.canLoadTextModel(false, false, false, true))
        assertFalse(SharedLlmGate.canStartHandsFree(true))
    }
    @Test fun `other flags still block without roundtable`() {
        assertFalse(SharedLlmGate.canSend(true, false, false, false, false, false))
        assertFalse(SharedLlmGate.canRunAgent(false, true, false, false, false))
        assertFalse(SharedLlmGate.canAskNotes(false, true, false))
        assertFalse(SharedLlmGate.canRunAgent(true, false, false, false, false))
        assertFalse(SharedLlmGate.canAskNotes(true, false, false))
    }
    @Test fun `hands-free internal send unaffected when no roundtable`() {
        // HF идёт через тот же send: без стола ничего не меняется
        assertTrue(SharedLlmGate.canSend(false, false, false, false, false, false))
    }
}
