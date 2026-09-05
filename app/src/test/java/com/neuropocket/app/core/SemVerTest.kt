package com.neuropocket.app.core

import org.junit.Assert.*
import org.junit.Test

class SemVerTest {
    @Test fun `equal ignores v prefix`() {
        assertEquals(0, SemVer.compare("v1.24.0", "1.24.0"))
    }
    @Test fun `plan suffix older than release`() {
        assertTrue(SemVer.compare("1.24.0-plan5", "1.24.0") < 0)
        assertTrue(SemVer.compare("1.25.0-rc.1", "1.25.0") < 0)
    }
    @Test fun `rc ordering`() {
        assertTrue(SemVer.compare("1.25.0-rc.2", "1.25.0-rc.1") > 0)
        assertTrue(SemVer.compare("1.25.0-rc.1", "1.25.0-rc.2") < 0)
    }
    @Test fun `newer detection`() {
        assertTrue(SemVer.isNewer("v1.25.0", "1.24.0-plan5"))
        assertTrue(SemVer.isNewer("v1.24.0", "1.24.0-plan5"))
        assertFalse(SemVer.isNewer("1.24.0-plan5", "1.24.0"))
        assertFalse(SemVer.isNewer("1.24.0", "1.24.0"))
        assertFalse(SemVer.isNewer("1.23.0", "1.24.0"))
    }
    @Test fun `numeric not lexicographic`() {
        assertTrue(SemVer.compare("1.10.0", "1.9.0") > 0)
        assertTrue(SemVer.compare("1.24.0", "1.24") == 0)
    }
}
