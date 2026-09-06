package com.neuropocket.app.core

import org.junit.Assert.*
import org.junit.Test

class DeterministicUtilsTest {

    @Test fun `calc basic arithmetic`() {
        val r = DeterministicUtils.calc("2 + 3 * 4")
        assertTrue(r is DeterministicUtils.CalcResult.Ok)
        assertEquals("14", (r as DeterministicUtils.CalcResult.Ok).formatted)
    }

    @Test fun `calc parentheses and division`() {
        val r = DeterministicUtils.calc("(10 - 4) / 3")
        assertTrue(r is DeterministicUtils.CalcResult.Ok)
        assertEquals("2", (r as DeterministicUtils.CalcResult.Ok).formatted)
    }

    @Test fun `calc division by zero is error`() {
        val r = DeterministicUtils.calc("5 / 0")
        assertTrue(r is DeterministicUtils.CalcResult.Err)
    }

    @Test fun `calc rejects letters injection`() {
        val r = DeterministicUtils.calc("2 + System.exit(1)")
        assertTrue(r is DeterministicUtils.CalcResult.Err)
    }

    @Test fun `calc power and percent`() {
        val r = DeterministicUtils.calc("2 ^ 10")
        assertEquals("1024", (r as DeterministicUtils.CalcResult.Ok).formatted)
        val m = DeterministicUtils.calc("10 % 3")
        assertEquals("1", (m as DeterministicUtils.CalcResult.Ok).formatted)
    }

    @Test fun `unit converter length`() {
        val km = DeterministicUtils.convert(1000.0, "m", "km", "length")
        assertNotNull(km)
        assertEquals(1.0, km!!, 1e-9)
    }

    @Test fun `unit converter temp`() {
        assertEquals(32.0, DeterministicUtils.convert(0.0, "C", "F", "temp")!!, 1e-9)
        assertEquals(273.15, DeterministicUtils.convert(0.0, "C", "K", "temp")!!, 1e-9)
    }

    @Test fun `unit converter unknown returns null`() {
        assertNull(DeterministicUtils.convert(1.0, "m", "kg", "length"))
    }

    @Test fun `json formatter valid and invalid`() {
        val ok = DeterministicUtils.formatJson("""{"a":1,"b":[1,2]}""")
        assertTrue(ok is DeterministicUtils.JsonResult.Ok)
        assertTrue((ok as DeterministicUtils.JsonResult.Ok).pretty.contains("\"a\""))
        val bad = DeterministicUtils.formatJson("{oops")
        assertTrue(bad is DeterministicUtils.JsonResult.Err)
    }

    @Test fun `sha256 known vector`() {
        // SHA-256("abc") — известный вектор
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            DeterministicUtils.sha256("abc")
        )
    }

    @Test fun `base64 roundtrip`() {
        val enc = DeterministicUtils.base64Encode("Привет, NeuroPocket!")
        val dec = DeterministicUtils.base64Decode(enc)
        assertTrue(dec is DeterministicUtils.B64Result.Ok)
        assertEquals("Привет, NeuroPocket!", (dec as DeterministicUtils.B64Result.Ok).text)
        assertTrue(DeterministicUtils.base64Decode("!!!not-base64!!!") is DeterministicUtils.B64Result.Err)
    }

    @Test fun `url roundtrip`() {
        val enc = DeterministicUtils.urlEncode("привет мир & co")
        val dec = DeterministicUtils.urlDecode(enc)
        assertTrue(dec is DeterministicUtils.UrlResult.Ok)
        assertEquals("привет мир & co", (dec as DeterministicUtils.UrlResult.Ok).text)
    }

    @Test fun `text stats counts`() {
        val s = DeterministicUtils.textStats("Привет мир. Это тест!")
        assertEquals(2, s.sentences)
        assertEquals(4, s.words)
        assertTrue(s.chars > 0)
        assertTrue(s.readingMin >= 0)
    }

    @Test fun `uuid format`() {
        val u = DeterministicUtils.newUuid()
        assertTrue(u.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        assertNotEquals(u, DeterministicUtils.newUuid())
    }
}
