package com.neuropocket.app.core

import org.junit.Assert.*
import org.junit.Test

class NativeInstallTest {
    private val PIN = AssetManifest.SD_ENGINE.sha256Hex

    @Test fun `sd legacy with trusted sha copies`() {
        assertEquals(
            NativeInstall.LegacyDecision.COPY_TRUSTED,
            NativeInstall.decideLegacySd(true, PIN)
        )
        assertEquals(
            NativeInstall.LegacyDecision.COPY_TRUSTED,
            NativeInstall.decideLegacySd(true, PIN.uppercase())
        )
    }
    @Test fun `sd legacy size-only without sha quarantines`() {
        // размер совпал, но SHA нет/чужой — quarantine (size-only запрещён)
        assertEquals(
            NativeInstall.LegacyDecision.QUARANTINE,
            NativeInstall.decideLegacySd(true, null)
        )
        assertEquals(
            NativeInstall.LegacyDecision.QUARANTINE,
            NativeInstall.decideLegacySd(true, "0".repeat(64))
        )
    }
    @Test fun `sd absent quarantines (nothing to do)`() {
        assertEquals(
            NativeInstall.LegacyDecision.QUARANTINE,
            NativeInstall.decideLegacySd(false, PIN)
        )
    }
    @Test fun `voice legacy never trusted`() {
        // PREFERRED: legacy extracted binaries не доверяем вообще
        assertEquals(
            NativeInstall.LegacyDecision.QUARANTINE,
            NativeInstall.decideLegacyVoice()
        )
    }
}
