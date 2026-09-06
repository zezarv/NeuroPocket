package com.neuropocket.app.core

import com.neuropocket.app.data.SocialPost

/**
 * Phase B: честная personal-соцсеть без fake semantics.
 * - новый пост стартует с 0 лайков (модель);
 * - репост ссылается на оригинал (repostOfId);
 * - автопостинг: duplicate prevention, cooldown, daily cap, pause;
 * - режимы называются честно: REMOTE (провайдер) / TEMPLATE (шаблоны).
 */
object SocialPolicy {
    /** Минимум между автопостами (защита от спама при ретраях WorkManager). */
    const val AUTOPOST_COOLDOWN_H = 2L
    /** Дневной лимит автопостов. */
    const val AUTOPOST_DAILY_CAP = 6
    /** Окно проверки дубликатов (последние N постов). */
    const val DUP_WINDOW = 30

    fun normalize(text: String): String =
        text.trim().lowercase().replace(Regex("\\s+"), " ")

    /** Дубликат, если нормализованный текст совпадает с кем-то из недавних. */
    fun isDuplicate(newText: String, recent: List<SocialPost>, window: Int = DUP_WINDOW): Boolean {
        val n = normalize(newText)
        if (n.isEmpty()) return false
        return recent.take(window).any { normalize(it.text) == n }
    }

    fun countToday(posts: List<SocialPost>, now: Long = System.currentTimeMillis()): Int {
        val day = now / 86_400_000L
        return posts.count { (it.ts / 86_400_000L) == day }
    }

    /**
     * Можно ли делать автопост сейчас. null = можно, иначе честная причина.
     */
    fun autopostBlockReason(
        now: Long,
        lastAutopostTs: Long,
        posts: List<SocialPost>,
        paused: Boolean,
        intervalHours: Int
    ): String? {
        if (paused) return "Автопостинг на паузе."
        if (intervalHours <= 0) return "Автопостинг выключен."
        if (lastAutopostTs > 0 && now - lastAutopostTs < AUTOPOST_COOLDOWN_H * 3_600_000L) {
            return "Cooldown: последний автопост был недавно."
        }
        if (countToday(posts, now) >= AUTOPOST_DAILY_CAP) {
            return "Дневной лимит ($AUTOPOST_DAILY_CAP/день) исчерпан."
        }
        return null
    }

    /** Честный режим автопостинга по активному провайдеру. */
    fun autopostMode(activeProviderId: String): String =
        if (activeProviderId != "local" && activeProviderId != "mock") "REMOTE" else "TEMPLATE"

    fun autopostModeDescription(activeProviderId: String): String = when (autopostMode(activeProviderId)) {
        "REMOTE" -> "REMOTE: фоновая генерация через выбранный API-провайдер."
        else -> "TEMPLATE: локальные шаблоны (помечены «Шаблон»). Фоновая работа local LLM ненадёжна и не изображается как ИИ."
    }

    /** Подпись происхождения поста для UI. */
    fun originLabel(aiMade: Boolean, template: Boolean): String? = when {
        template -> "Шаблон"
        aiMade -> "ИИ"
        else -> null
    }
}
