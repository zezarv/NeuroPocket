package com.neuropocket.app.core

import org.junit.Assert.*
import org.junit.Test

class EngineStatesTest {
    @Test fun `voice failed download is terminal error`() {
        assertEquals(
            VoiceEngineState.ERROR,
            VoiceEngine.next(VoiceEngineState.DOWNLOADING, EngineEvent.DOWNLOAD_FAILED)
        )
        // не застревает в DOWNLOADING
        assertNotEquals(
            VoiceEngineState.DOWNLOADING,
            VoiceEngine.next(VoiceEngineState.DOWNLOADING, EngineEvent.DOWNLOAD_FAILED)
        )
    }
    @Test fun `voice cancel returns to missing`() {
        assertEquals(
            VoiceEngineState.MISSING,
            VoiceEngine.next(VoiceEngineState.DOWNLOADING, EngineEvent.DOWNLOAD_CANCELLED)
        )
        assertEquals(
            VoiceEngineState.MISSING,
            VoiceEngine.next(VoiceEngineState.ERROR, EngineEvent.DOWNLOAD_CANCELLED)
        )
    }
    @Test fun `voice dismiss failed keeps error`() {
        assertEquals(
            VoiceEngineState.ERROR,
            VoiceEngine.next(VoiceEngineState.ERROR, EngineEvent.DOWNLOAD_DISMISSED)
        )
    }
    @Test fun `voice dismiss non-error goes missing`() {
        assertEquals(
            VoiceEngineState.MISSING,
            VoiceEngine.next(VoiceEngineState.DOWNLOADING, EngineEvent.DOWNLOAD_DISMISSED)
        )
    }
    @Test fun `voice happy path`() {
        var s = VoiceEngineState.MISSING
        s = VoiceEngine.next(s, EngineEvent.START_DOWNLOAD)
        assertEquals(VoiceEngineState.DOWNLOADING, s)
        s = VoiceEngine.next(s, EngineEvent.DOWNLOAD_OK)
        assertEquals(VoiceEngineState.VERIFYING, s)
        s = VoiceEngine.next(s, EngineEvent.VERIFY_OK)
        assertEquals(VoiceEngineState.INSTALLING, s)
        s = VoiceEngine.next(s, EngineEvent.LOAD_OK)
        assertEquals(VoiceEngineState.READY, s)
    }
    @Test fun `voice verify fail quarantines to error`() {
        assertEquals(
            VoiceEngineState.ERROR,
            VoiceEngine.next(VoiceEngineState.VERIFYING, EngineEvent.VERIFY_FAIL)
        )
    }
    @Test fun `voice retry reopens`() {
        assertEquals(
            VoiceEngineState.MISSING,
            VoiceEngine.next(VoiceEngineState.ERROR, EngineEvent.RETRY)
        )
    }
    @Test fun `sd mirrors terminal semantics`() {
        assertEquals(
            SdEngineState.ERROR,
            SdEngine.next(SdEngineState.DOWNLOADING, EngineEvent.DOWNLOAD_FAILED)
        )
        assertEquals(
            SdEngineState.MISSING,
            SdEngine.next(SdEngineState.DOWNLOADING, EngineEvent.DOWNLOAD_CANCELLED)
        )
        assertEquals(
            SdEngineState.ERROR,
            SdEngine.next(SdEngineState.ERROR, EngineEvent.DOWNLOAD_DISMISSED)
        )
        var s = SdEngineState.MISSING
        s = SdEngine.next(s, EngineEvent.START_DOWNLOAD)
        s = SdEngine.next(s, EngineEvent.DOWNLOAD_OK)
        s = SdEngine.next(s, EngineEvent.VERIFY_OK)
        s = SdEngine.next(s, EngineEvent.LOAD_OK)
        assertEquals(SdEngineState.READY, s)
    }
    @Test fun `legacy file state is missing not dead end`() {
        // тупикового FILE больше нет: неизвестное легаси честно MISSING
        assertEquals(VoiceEngineState.MISSING, VoiceEngine.fromLegacy("file"))
        assertEquals(VoiceEngineState.MISSING, VoiceEngine.fromLegacy(""))
        assertEquals(VoiceEngineState.MISSING, VoiceEngine.fromLegacy(null))
        assertEquals(VoiceEngineState.READY, VoiceEngine.fromLegacy("ok"))
        assertEquals(VoiceEngineState.ERROR, VoiceEngine.fromLegacy("error"))
    }
}
