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
    data class Bullets(val items: List<String>, val numbered: Boolean) : MdPart
    data class Table(val head: List<String>, val rows: List<List<String>>) : MdPart
}

private val bulletRe = Regex("^(\\s*)([-*•]|\\d+[.)])\\s+(.+)")
private val tableSepRe = Regex("^\\|?[\\s:|-]+\\|?[\\s:|-]*$")

/** Режем текстовый кусок на абзацы / списки / таблицы. */
private fun splitBlocks(src: String): List<MdPart> {
    val out = mutableListOf<MdPart>()
    val lines = src.split("\n")
    var i = 0
    val para = StringBuilder()
    fun flushPara() {
        if (para.isNotBlank()) out.add(MdPart.Text(para.toString().trimEnd()))
        para.clear()
    }
    while (i < lines.size) {
        val ln = lines[i]
        val bm = bulletRe.find(ln)
        if (bm != null) {
            flushPara()
            val items = mutableListOf<String>()
            var numbered = bm.groupValues[2].firstOrNull()?.isDigit() == true
            while (i < lines.size) {
                val m2 = bulletRe.find(lines[i]) ?: break
                items.add(m2.groupValues[3])
                i++
            }
            out.add(MdPart.Bullets(items, numbered))
            continue
        }
        if (ln.trimStart().startsWith("|") && i + 1 < lines.size &&
            tableSepRe.matches(lines[i + 1].trim())
        ) {
            flushPara()
            fun cells(s: String): List<String> =
                s.trim().trim('|').split("|").map { it.trim().take(60) }
            val head = cells(ln)
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                rows.add(cells(lines[i]))
                i++
            }
            if (head.isNotEmpty()) out.add(MdPart.Table(head, rows.take(20)))
            continue
        }
        para.appendLine(ln)
        i++
    }
    flushPara()
    return out
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

@Composable
private fun TableRow(cells: List<String>, fontSize: androidx.compose.ui.unit.TextUnit, header: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.forEach { c ->
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    c.ifBlank { "—" },
                    fontSize = fontSize * 0.92f,
                    fontWeight = if (header) FontWeight.Bold else null,
                    maxLines = 6
                )
            }
        }
    }
}

/** Текст ответа ИИ: код-блоки, списки, таблицы, `код`, **жирный**. */
@Composable
fun MarkdownText(text: String, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val parts = remember(text) { splitFences(text) }
    val monoBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    // текстовые куски дополнительно бьём на абзацы/списки/таблицы
    val rich = remember(text) {
        parts.flatMap { if (it is MdPart.Text) splitBlocks(it.text) else listOf(it) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rich.forEach { p ->
            when (p) {
                is MdPart.Text -> {
                    if (p.text.isNotBlank()) {
                        SelectionContainer {
                            Text(inlineStyled(p.text, monoBg), fontSize = fontSize)
                        }
                    }
                }
                is MdPart.Bullets -> {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        p.items.forEachIndexed { idx, s ->
                            Row {
                                Text(
                                    if (p.numbered) "${idx + 1}. " else "• ",
                                    fontSize = fontSize, color = MaterialTheme.colorScheme.primary
                                )
                                SelectionContainer {
                                    Text(inlineStyled(s, monoBg), fontSize = fontSize,
                                        modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                is MdPart.Table -> {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(8.dp)) {
                            TableRow(p.head, fontSize, header = true)
                            HorizontalDivider()
                            p.rows.forEach { r ->
                                TableRow(r, fontSize, header = false)
                            }
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
