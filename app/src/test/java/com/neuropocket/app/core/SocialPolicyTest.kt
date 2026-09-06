package com.neuropocket.app.core

import com.neuropocket.app.data.SocialPost
import org.junit.Assert.*
import org.junit.Test

class SocialPolicyTest {

    private fun post(text: String, ts: Long = 1_700_000_000_000L) =
        SocialPost(authorId = "a", text = text, ts = ts)

    @Test fun `new post starts at zero likes`() {
        val p = SocialPost(authorId = "a", text = "hello")
        assertEquals(0, p.likes)
        assertFalse(p.liked)
    }

    @Test fun `repost keeps reference to original`() {
        val orig = post("original text")
        val re = SocialPost(authorId = "b", text = "", repostOfId = orig.id)
        assertEquals(orig.id, re.repostOfId)
        // репост — не копия текста
        assertNotEquals(orig.text, re.text)
    }

    @Test fun `duplicate detection ignores case and spacing`() {
        val recent = listOf(post("Привет  мир"))
        assertTrue(SocialPolicy.isDuplicate("  привет мир ", recent))
        assertFalse(SocialPolicy.isDuplicate("совсем другое", recent))
        assertFalse(SocialPolicy.isDuplicate("", recent))
    }

    @Test fun `duplicate window limits lookback`() {
        val recent = (1..40).map { post("post $it") }
        // "post 1" за пределами окна 30 (последние 30 — это post 11..40)? take(30) берёт первые 30 списка.
        assertTrue(SocialPolicy.isDuplicate("post 5", recent, window = 30))
        assertFalse(SocialPolicy.isDuplicate("post 35", recent.take(30).dropLast(10), window = 20))
    }

    @Test fun `autopost blocked when paused`() {
        val r = SocialPolicy.autopostBlockReason(
            now = 10_000_000_000_000L, lastAutopostTs = 0L,
            posts = emptyList(), paused = true, intervalHours = 6
        )
        assertNotNull(r)
        assertTrue(r!!.contains("паузе", ignoreCase = true))
    }

    @Test fun `autopost blocked when off`() {
        val r = SocialPolicy.autopostBlockReason(10_000_000_000_000L, 0L, emptyList(), false, 0)
        assertNotNull(r)
    }

    @Test fun `autopost cooldown blocks quick rerun`() {
        val now = 10_000_000_000_000L
        val r = SocialPolicy.autopostBlockReason(now, now - 30 * 60_000L, emptyList(), false, 6)
        assertNotNull(r)
        assertTrue(r!!.contains("Cooldown"))
    }

    @Test fun `autopost allowed after cooldown`() {
        val now = 10_000_000_000_000L
        val r = SocialPolicy.autopostBlockReason(
            now, now - 3 * 3_600_000L, emptyList(), false, 6
        )
        assertNull(r)
    }

    @Test fun `autopost daily cap blocks`() {
        val now = 1_700_000_000_000L
        val posts = (1..SocialPolicy.AUTOPOST_DAILY_CAP).map { post("p$it", ts = now - it * 60_000L) }
        val r = SocialPolicy.autopostBlockReason(now, 0L, posts, false, 6)
        assertNotNull(r)
        assertTrue(r!!.contains("Лимит", ignoreCase = true) || r.contains("лимит", ignoreCase = true))
    }

    @Test fun `autopost mode honest by provider`() {
        assertEquals("REMOTE", SocialPolicy.autopostMode("prov-123"))
        assertEquals("TEMPLATE", SocialPolicy.autopostMode("local"))
        assertEquals("TEMPLATE", SocialPolicy.autopostMode("mock"))
        assertTrue(SocialPolicy.autopostModeDescription("local").contains("TEMPLATE"))
        assertTrue(SocialPolicy.autopostModeDescription("prov-1").contains("REMOTE"))
    }

    @Test fun `origin labels honest`() {
        assertEquals("Шаблон", SocialPolicy.originLabel(aiMade = false, template = true))
        assertEquals("ИИ", SocialPolicy.originLabel(aiMade = true, template = false))
        assertNull(SocialPolicy.originLabel(aiMade = false, template = false))
        // template никогда не выдаётся за ИИ
        assertNotEquals("ИИ", SocialPolicy.originLabel(aiMade = true, template = true))
    }

    @Test fun `mock disclosure for social text`() {
        assertTrue(CapabilityDisclosure.isMockOutput("кофе + ночной код — это локальный ответ-заготовка v1"))
        assertEquals("Mock / template fallback", CapabilityDisclosure.engineBadge("x", false, "y"))
    }
}
