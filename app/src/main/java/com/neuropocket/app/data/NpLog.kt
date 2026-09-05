package com.neuropocket.app.data

import android.app.Application
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Кольцевой лог приложения (последние 300 строк) + перехват падений.
 * Читается на экране диагностики, отправляется разработчику одной кнопкой.
 */
object NpLog {
    private val buf = ConcurrentLinkedQueue<String>()
    private const val MAX = 300

    private fun line(lv: String, tag: String, msg: String): String {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        return "$ts $lv/$tag: ${msg.take(500)}"
    }

    @Synchronized
    private fun push(s: String) {
        buf.add(s)
        while (buf.size > MAX) buf.poll()
    }

    fun d(tag: String, msg: String) = push(line("D", tag, msg))
    fun i(tag: String, msg: String) = push(line("I", tag, msg))
    fun w(tag: String, msg: String) = push(line("W", tag, msg))
    fun e(tag: String, msg: String) = push(line("E", tag, msg))

    fun dump(): String = buf.joinToString("\n").ifBlank { "(лог пуст)" }
    fun clear() = buf.clear()

    fun deviceInfo(app: Application): String = buildString {
        append("NeuroPocket diag\n")
        append("model=").append(Build.MODEL).append(" android=").append(Build.VERSION.RELEASE)
        append(" sdk=").append(Build.VERSION.SDK_INT).append("\n")
        val mi = android.app.ActivityManager.MemoryInfo()
        (app.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
            .getMemoryInfo(mi)
        append("ramFree=").append(mi.availMem / 1048576).append("MB total=")
        append(mi.totalMem / 1048576).append("MB low=").append(mi.lowMemory).append("\n")
        val ext = app.getExternalFilesDir(null)
        append("filesFree=").append((ext?.usableSpace ?: -1) / 1048576).append("MB\n")
        append("abi=").append(Build.SUPPORTED_ABIS.joinToString()).append("\n")
    }

    fun crashFile(app: Application): File = File(app.getExternalFilesDir(null), "models/crash-last.log")
}

class NpApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sb = StringBuilder()
                sb.append("CRASH ").append(Date()).append(" thread=").append(t.name).append("\n")
                sb.append(e.stackTraceToString().take(6000)).append("\n---LOG---\n")
                sb.append(NpLog.dump())
                NpLog.crashFile(this).writeText(sb.toString())
            } catch (_: Exception) { }
            prev?.uncaughtException(t, e)
        }
        NpLog.i("app", "start")
    }
}
