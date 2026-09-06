package com.neuropocket.app.core

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    @Test fun `newer release available`() {
        assertEquals(UpdatePolicy.Decision.AVAILABLE, UpdatePolicy.decide("v1.25.0", "1.24.0"))
        assertTrue(UpdatePolicy.shouldOffer("v1.25.0", "1.24.0"))
    }
    @Test fun `plan suffix older than release`() {
        assertEquals(
            UpdatePolicy.Decision.AVAILABLE,
            UpdatePolicy.decide("v1.24.0", "1.24.0-plan5")
        )
    }
    @Test fun `same version up to date`() {
        assertEquals(UpdatePolicy.Decision.UP_TO_DATE, UpdatePolicy.decide("v1.24.0", "1.24.0"))
        assertFalse(UpdatePolicy.shouldOffer("v1.24.0", "1.24.0"))
    }
    @Test fun `local newer no offer`() {
        assertEquals(UpdatePolicy.Decision.NEWER_LOCAL, UpdatePolicy.decide("v1.23.0", "1.24.0"))
        assertFalse(UpdatePolicy.shouldOffer("v1.23.0", "1.24.0"))
    }
    @Test fun `blank unknown`() {
        assertEquals(UpdatePolicy.Decision.UNKNOWN, UpdatePolicy.decide(null, "1.24.0"))
        assertEquals(UpdatePolicy.Decision.UNKNOWN, UpdatePolicy.decide("v1.25.0", null))
        assertEquals(UpdatePolicy.Decision.UNKNOWN, UpdatePolicy.decide("", ""))
    }
    @Test fun `downgrade never offered`() {
        // verifyUpdateApk дополнительно блокирует по versionCode; политика — по имени
        assertFalse(UpdatePolicy.shouldOffer("1.24.0-rc.1", "1.24.0"))
    }
}
