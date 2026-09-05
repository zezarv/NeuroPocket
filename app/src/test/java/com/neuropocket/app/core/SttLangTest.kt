package com.neuropocket.app.core

import org.junit.Assert.*
import org.junit.Test

class SttLangTest {
    @Test fun `normalize keeps allowed`() {
        assertEquals("ru", SttLang.normalize("ru"))
        assertEquals("en", SttLang.normalize("en"))
        assertEquals("auto", SttLang.normalize("auto"))
    }
    @Test fun `normalize case-insensitive and trim`() {
        assertEquals("ru", SttLang.normalize(" RU "))
        assertEquals("en", SttLang.normalize("EN"))
        assertEquals("auto", SttLang.normalize("Auto"))
    }
    @Test fun `normalize defaults to ru`() {
        assertEquals("ru", SttLang.normalize(null))
        assertEquals("ru", SttLang.normalize(""))
        assertEquals("ru", SttLang.normalize("de"))
        assertEquals("ru", SttLang.normalize("???"))
    }
}
