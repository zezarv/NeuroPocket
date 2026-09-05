package com.neuropocket.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neuropocket.app.engine.MockEngine
import com.neuropocket.app.engine.RemoteEngine
/**
 * Автопост в ленту: случайная персона, движок — выбранный провайдер
 * (если это API), иначе локальные шаблоны. Без LLM в фоне батарею не жрём.
 */
class AutopostWorker(appCtx: Context, params: WorkerParameters) : CoroutineWorker(appCtx, params) {
    override suspend fun doWork(): Result {
        return try {
            val personas = Store.loadPersonas(applicationContext)
            if (personas.isEmpty()) return Result.success()
            val per = personas.random()
            val activeId = Store.getActiveProvider(applicationContext)
            val text = if (activeId != "local" && activeId != "mock") {
                val p = Store.loadProviders(applicationContext).find { it.id == activeId && it.enabled }
                if (p != null) {
                    try {
                        val raw = RemoteEngine(p, 256, 0.9f).generate(
                            emptyList(), per,
                            "Придумай 1 короткий живой пост для соцсети от лица «${per.name}» " +
                                "(${per.desc.ifBlank { per.systemPrompt.take(120) }}). " +
                                "До 200 символов, 1–2 хэштега. Без пояснений."
                        )
                        raw.lines().map { it.trim() }.firstOrNull { it.length > 8 }?.take(300)
                    } catch (_: Exception) { null }
                } else null
            } else null
            val final = text ?: MockEngine().randomPost()
            val ai = text != null
            val posts = Store.loadPosts(applicationContext).toMutableList()
            posts.add(0, SocialPost(authorId = per.id, text = final, aiMade = ai))
            Store.savePosts(applicationContext, posts.take(200))
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
