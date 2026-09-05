package com.neuropocket.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File

/**
 * Тихий автобэкап данных (без ключей API) раз в неделю.
 * Хранит последние 3 файла в models/, старые удаляет.
 */
class BackupWorker(appCtx: Context, params: WorkerParameters) : CoroutineWorker(appCtx, params) {
    override suspend fun doWork(): Result {
        return try {
            val dump = Store.dumpData(applicationContext)
            val settings = mapOf<String, Any>(
                "theme" to Store.getTheme(applicationContext),
                "accent" to Store.getAccent(applicationContext),
                "maxTokens" to Store.getMaxTokens(applicationContext),
                "topP" to Store.getTopP(applicationContext),
                "topK" to Store.getTopK(applicationContext),
                "ctxSize" to Store.getCtxSize(applicationContext),
                "threads" to Store.getThreads(applicationContext),
                "gpuLayers" to Store.getGpuLayers(applicationContext),
                "textScale" to Store.getTextScale(applicationContext),
                "ttsRate" to Store.getTtsRate(applicationContext),
                "ttsPitch" to Store.getTtsPitch(applicationContext),
                "keepOn" to Store.getKeepOn(applicationContext),
                "wifiOnly" to Store.getWifiOnly(applicationContext),
                "activeProvider" to Store.getActiveProvider(applicationContext)
            )
            val f = Backup.make(
                applicationContext,
                dump["personas"] ?: "[]", dump["sessions"] ?: "[]", dump["msgmap"] ?: "{}",
                dump["chars"] ?: "[]", dump["posts"] ?: "[]", dump["comments"] ?: "[]",
                dump["providers"] ?: "[]",
                settings, false, ""
            )
            val auto = File(f.parent, "auto-" + f.name)
            if (f.renameTo(auto)) {
                val olds = f.parentFile?.listFiles { x -> x.name.startsWith("auto-NeuroPocket-backup") }
                    ?.sortedByDescending { it.lastModified() } ?: emptyList()
                olds.drop(3).forEach { try { it.delete() } catch (_: Exception) {} }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
