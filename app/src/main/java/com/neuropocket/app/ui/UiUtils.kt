package com.neuropocket.app.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.withContext

/** Имя файла из Uri (DISPLAY_NAME) с чисткой от путей. */
fun uriFileName(ctx: Context, uri: Uri, fallback: String): String {
    return try {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) {
                val n = c.getString(idx) ?: fallback
                return n.substringAfterLast('/').substringAfterLast('\\').take(120).ifBlank { fallback }
            }
            fallback
        } ?: fallback
    } catch (_: Exception) { fallback }
}

/**
 * Копирует Uri в папку models/, сохраняя оригинальное имя и расширение.
 * Если имя занято — добавляет суффикс. Возвращает файл или null.
 */
fun Context.copyUriToModels(uri: Uri, fallbackPrefix: String): File? {
    return try {
        val ext = uriFileName(this, uri, "").substringAfterLast('.', "")
        val base = uriFileName(this, uri, "$fallbackPrefix-${System.currentTimeMillis()}")
            .replace(Regex("[^A-Za-z0-9а-яА-ЯёЁ._\\- ]"), "_").trim().ifBlank { "$fallbackPrefix-${System.currentTimeMillis()}" }
        val name = if ('.' in base) base else if (ext.isNotEmpty()) "$base.$ext" else base
        val dir = File(getExternalFilesDir(null), "models").apply { mkdirs() }
        var out = File(dir, name)
        var k = 1
        while (out.exists()) {
            val stem = name.substringBeforeLast('.', name)
            val e = if ('.' in name) "." + name.substringAfterLast('.') else ""
            out = File(dir, "$stem-$k$e")
            if (++k > 99) break
        }
        contentResolver.openInputStream(uri)?.use { ins -> out.outputStream().use { ins.copyTo(it) } }
            ?: return null
        out
    } catch (_: Exception) { null }
}

/** Аватар персоны: фото (декод в фоне) или эмодзи. */
@Composable
fun AvatarView(
    path: String,
    emoji: String,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = CircleShape
) {
    if (path.isNotBlank()) {
        val bmp by produceState<android.graphics.Bitmap?>(initialValue = null, path) {
            value = withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(path, opts)
                    var s = 1
                    val maxSide = maxOf(opts.outWidth, opts.outHeight)
                    while (maxSide / (s * 2) >= 256) s *= 2
                    android.graphics.BitmapFactory.Options().apply { inSampleSize = s }
                        .let { android.graphics.BitmapFactory.decodeFile(path, it) }
                } catch (_: Exception) { null }
            }
        }
        if (bmp != null) {
            val b: android.graphics.Bitmap = bmp!!
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                modifier = modifier.size(size).clip(shape)
            )
            return
        }
    }
    Surface(shape = shape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
        Text(emoji.ifBlank { "\uD83D\uDE00" }, modifier = modifier.padding((size.value / 5).dp))
    }
}

/** мм:сс.мс из миллисекунд. */
fun fmtTime(ms: Long): String {
    val m = ms / 60000
    val s = (ms % 60000) / 1000
    return "%02d:%02d".format(m, s)
}

/** Короткое «2 ч назад» / дата. */
fun timeAgo(ts: Long): String {
    val d = System.currentTimeMillis() - ts
    return when {
        d < 60_000 -> "только что"
        d < 3_600_000 -> "${d / 60_000} мин назад"
        d < 86_400_000 -> "${d / 3_600_000} ч назад"
        else -> java.text.SimpleDateFormat("dd.MM", java.util.Locale.getDefault()).format(java.util.Date(ts))
    }
}
