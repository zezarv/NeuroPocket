package com.neuropocket.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

private sealed interface MdPart {
    data class Text(val text: String) : MdPart
    data class Code(val lang: String, val code: String) : MdPart
}

private fun splitFences(src: String): List<MdPart> {
    val out = mutableListOf<MdPart>()
    var rest = src
    while (true) {
        val open = rest.indexOf("```")
        if (open < 0) {
            if (rest.isNotEmpty()) out.add(MdPart.Text(rest))
            break
        }
        if (open > 0) out.add(MdPart.Text(rest.substring(0, open)))
        val close = rest.indexOf("```", open + 3)
        if (close < 0) {
            // незакрытый забор — считаем кодом до конца
            val body = rest.substring(open + 3)
            val nl = body.indexOf('\n')
            if (nl >= 0) out.add(MdPart.Code(body.substring(0, nl).trim().take(20), body.substring(nl + 1)))
            else out.add(MdPart.Code("", body))
            break
        }
        val body = rest.substring(open + 3, close)
        val nl = body.indexOf('\n')
        if (nl >= 0) out.add(MdPart.Code(body.substring(0, nl).trim().take(20), body.substring(nl + 1).trimEnd()))
        else out.add(MdPart.Code("", body.trim()))
        rest = rest.substring(close + 3)
    }
    return out
}

private fun inlineStyled(src: String, monoBg: androidx.compose.ui.graphics.Color): AnnotatedString {
    // `код` и **жирный**
    return buildAnnotatedString {
        var i = 0
        while (i < src.length) {
            val bt = src.indexOf('`', i)
            val bd = src.indexOf("**", i)
            val next = listOf(bt, bd).filter { it >= 0 }.minOrNull()
            if (next == null) {
                append(src.substring(i))
                break
            }
            append(src.substring(i, next))
            if (next == bt) {
                val end = src.indexOf('`', bt + 1)
                if (end < 0) {
                    append(src.substring(bt))
                    break
                }
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = monoBg)) {
                    append(src.substring(bt + 1, end))
                }
                i = end + 1
            } else {
                val end = src.indexOf("**", bd + 2)
                if (end < 0) {
                    append(src.substring(bd))
                    break
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(src.substring(bd + 2, end))
                }
                i = end + 2
            }
        }
    }
}

/** Текст ответа ИИ: код-блоки с кнопкой копирования, `код`, **жирный**. */
@Composable
fun MarkdownText(text: String, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val parts = remember(text) { splitFences(text) }
    if (parts.size == 1 && parts[0] is MdPart.Text) {
        val monoBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        val styled = remember(text) { inlineStyled((parts[0] as MdPart.Text).text, monoBg) }
        SelectionContainer {
            Text(styled, fontSize = fontSize, modifier = modifier)
        }
        return
    }
    val monoBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        parts.forEach { p ->
            when (p) {
                is MdPart.Text -> {
                    if (p.text.isNotBlank()) {
                        SelectionContainer {
                            Text(inlineStyled(p.text, monoBg), fontSize = fontSize)
                        }
                    }
                }
                is MdPart.Code -> {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    p.lang.ifBlank { "код" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("np-code", p.code))
                                }) { Text("Копия") }
                            }
                            HorizontalDivider()
                            SelectionContainer {
                                Text(
                                    p.code,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = fontSize * 0.92f,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
