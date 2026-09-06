package com.neuropocket.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neuropocket.app.core.SocialPolicy
import com.neuropocket.app.engine.MockEngine
import com.neuropocket.app.engine.RemoteEngine

/**
 * Автопост в ленту (Phase B: честные режимы + безопасность).
 *
 * Режимы:
 *  - REMOTE: фоновая генерация через выбранный API-провайдер (только если разрешён);
 *  - TEMPLATE: локальные шаблоны, явно помеченные template=true (не выдаются за ИИ).
 * Фоновая работа local LLM ненадёжна — как "AI autopost" не изображается.
 *
 * Безопасность: pause, cooldown, daily cap, duplicate prevention.
 */
class AutopostWorker(appCtx: Context, params: WorkerParameters) : CoroutineWorker(appCtx, params) {
    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            val interval = Store.getAutopost(applicationContext)
            val paused = Store.isAutopostPaused(applicationContext)
            val last = Store.getLastAutopost(applicationContext)
            val existing = Store.loadPosts(applicationContext)
            val block = SocialPolicy.autopostBlockReason(now, last, existing, paused, interval)
            if (block != null) return Result.success() // честный пропуск, не ошибка

            val personas = Store.loadPersonas(applicationContext)
            if (personas.isEmpty()) return Result.success()
            val per = personas.random()
            val activeId = Store.getActiveProvider(applicationContext)
            var text: String? = null
            if (SocialPolicy.autopostMode(activeId) == "REMOTE") {
                val p = Store.loadProviders(applicationContext).find { it.id == activeId && it.enabled }
                if (p != null) {
                    try {
                        val raw = RemoteEngine(p, 256, 0.9f).generate(
                            emptyList(), per,
                            "Придумай 1 короткий живой пост для соцсети от лица «${per.name}» " +
                                "(${per.desc.ifBlank { per.systemPrompt.take(120) }}). " +
                                "До 200 символов, 1–2 хэштега. Без пояснений."
                        )
                        text = raw.lines().map { it.trim() }.firstOrNull { it.length > 8 }?.take(300)
                    } catch (_: Exception) { text = null }
                }
            }
            val post = if (text != null && !SocialPolicy.isDuplicate(text, existing)) {
                SocialPost(authorId = per.id, text = text, aiMade = true, template = false)
            } else if (text != null) {
                // remote-дубликат — не публикуем
                Store.setLastAutopost(applicationContext, now)
                return Result.success()
            } else {
                // TEMPLATE: до 4 попыток взять незаезженный шаблон
                val mock = MockEngine()
                var picked: String? = null
                for (i in 0 until 4) {
                    val t = mock.randomPost()
                    if (!SocialPolicy.isDuplicate(t, existing)) {
                        picked = t
                        break
                    }
                }
                val final = picked ?: return Result.success()
                SocialPost(authorId = per.id, text = final, aiMade = false, template = true)
            }
            val posts = (listOf(post) + existing).sortedByDescending { it.ts }.take(200)
            Store.savePosts(applicationContext, posts)
            Store.setLastAutopost(applicationContext, now)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
