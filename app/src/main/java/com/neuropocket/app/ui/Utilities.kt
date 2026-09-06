package com.neuropocket.app.ui

import android.content.ClipData
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.neuropocket.app.core.DeterministicUtils

/** Deterministic utilities: без LLM, чистый локальный код. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UtilitiesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    fun copy(text: String) {
        try {
            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("np", text))
        } catch (_: Exception) { }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Утилиты") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") } }
        )
    }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Text("Локально, без нейросети. QR — только если легко: пропущен (нет лёгкой зависимости).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            item { CalcCard(::copy) }
            item { ConverterCard(::copy) }
            item { JsonCard(::copy) }
            item { ShaCard(::copy) }
            item { Base64Card(::copy) }
            item { UrlCard(::copy) }
            item { StatsCard() }
            item { UuidCard(::copy) }
        }
    }
}

@Composable
private fun UtilCard(title: String, hint: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun CopyRow(result: String, onCopy: (String) -> Unit) {
    Row {
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { onCopy(result) }, enabled = result.isNotBlank()) { Text("Копия") }
    }
}

@Composable
private fun CalcCard(onCopy: (String) -> Unit) {
    var expr by remember { mutableStateOf("") }
    var out by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    UtilCard("Калькулятор", "Выражение: + - * / % ^ ( ). Без букв.") {
        OutlinedTextField(expr, { expr = it; err = "" }, label = { Text("2 * (3 + 4)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            when (val r = DeterministicUtils.calc(expr)) {
                is DeterministicUtils.CalcResult.Ok -> { out = r.formatted; err = "" }
                is DeterministicUtils.CalcResult.Err -> { out = ""; err = r.message }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Посчитать") }
        if (out.isNotBlank()) {
            Text("= $out", style = MaterialTheme.typography.titleMedium)
            CopyRow(out, onCopy)
        }
        if (err.isNotBlank()) Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ConverterCard(onCopy: (String) -> Unit) {
    var cat by remember { mutableStateOf("length") }
    var from by remember { mutableStateOf("m") }
    var to by remember { mutableStateOf("km") }
    var value by remember { mutableStateOf("1000") }
    var out by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    val units = DeterministicUtils.convertUnits(cat)
    UtilCard("Конвертер единиц", "Длина / масса / температура / данные.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("length" to "Длина", "mass" to "Масса", "temp" to "Температура", "data" to "Данные").forEach { (v, t) ->
                FilterChip(selected = cat == v, onClick = {
                    cat = v
                    val u = DeterministicUtils.convertUnits(v)
                    from = u.firstOrNull() ?: ""
                    to = u.getOrNull(1) ?: u.firstOrNull() ?: ""
                    out = ""; err = ""
                }, label = { Text(t) })
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value, { value = it; err = "" }, label = { Text("Значение") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UnitDrop(units, from, { from = it }, "Из", Modifier.weight(1f))
            UnitDrop(units, to, { to = it }, "В", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            val v = value.trim().replace(',', '.').toDoubleOrNull()
            if (v == null) {
                err = "Не число."; out = ""
            } else {
                val r = DeterministicUtils.convert(v, from, to, cat)
                if (r == null) {
                    err = "Нет такой пары единиц."; out = ""
                } else {
                    out = DeterministicUtils.formatDouble(r); err = ""
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Перевести") }
        if (out.isNotBlank()) {
            Text("= $out $to", style = MaterialTheme.typography.titleMedium)
            CopyRow(out, onCopy)
        }
        if (err.isNotBlank()) Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDrop(units: List<String>, cur: String, onPick: (String) -> Unit, label: String, mod: Modifier) {
    var exp by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = exp, onExpandedChange = { exp = it }, modifier = mod) {
        OutlinedTextField(cur, {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = exp) },
            modifier = Modifier.menuAnchor().fillMaxWidth(), singleLine = true)
        ExposedDropdownMenu(expanded = exp, onDismissRequest = { exp = false }) {
            units.forEach { u -> DropdownMenuItem(text = { Text(u) }, onClick = { onPick(u); exp = false }) }
        }
    }
}

@Composable
private fun JsonCard(onCopy: (String) -> Unit) {
    var src by remember { mutableStateOf("") }
    var out by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    UtilCard("JSON: формат + проверка", "Вставь JSON — получишь читаемый вид или ошибку.") {
        OutlinedTextField(src, { src = it; err = "" }, label = { Text("{\"a\":1}") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6)
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            when (val r = DeterministicUtils.formatJson(src)) {
                is DeterministicUtils.JsonResult.Ok -> { out = r.pretty; err = "" }
                is DeterministicUtils.JsonResult.Err -> { out = ""; err = r.message }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Форматировать") }
        if (out.isNotBlank()) {
            Text(out, style = MaterialTheme.typography.bodySmall)
            CopyRow(out, onCopy)
        }
        if (err.isNotBlank()) Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ShaCard(onCopy: (String) -> Unit) {
    var src by remember { mutableStateOf("") }
    var out by remember { mutableStateOf("") }
    UtilCard("SHA-256", "Хэш текста (проверка целостности).") {
        OutlinedTextField(src, { src = it }, label = { Text("Текст") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
        Spacer(Modifier.height(8.dp))
        Button(onClick = { out = if (src.isBlank()) "" else DeterministicUtils.sha256(src) }, modifier = Modifier.fillMaxWidth(), enabled = src.isNotBlank()) { Text("Хэш") }
        if (out.isNotBlank()) {
            Text(out, style = MaterialTheme.typography.bodySmall)
            CopyRow(out, onCopy)
        }
    }
}

@Composable
private fun Base64Card(onCopy: (String) -> Unit) {
    var src by remember { mutableStateOf("") }
    var out by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    UtilCard("Base64", "Кодирование/декодирование.") {
        OutlinedTextField(src, { src = it; err = "" }, label = { Text("Текст или Base64") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { out = DeterministicUtils.base64Encode(src); err = "" }, modifier = Modifier.weight(1f), enabled = src.isNotEmpty()) { Text("Закодировать") }
            OutlinedButton(onClick = {
                when (val r = DeterministicUtils.base64Decode(src)) {
                    is DeterministicUtils.B64Result.Ok -> { out = r.text; err = "" }
                    is DeterministicUtils.B64Result.Err -> { out = ""; err = r.message }
                }
            }, modifier = Modifier.weight(1f), enabled = src.isNotEmpty()) { Text("Декодировать") }
        }
        if (out.isNotBlank()) {
            Text(out.take(4000), style = MaterialTheme.typography.bodySmall)
            CopyRow(out, onCopy)
        }
        if (err.isNotBlank()) Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun UrlCard(onCopy: (String) -> Unit) {
    var src by remember { mutableStateOf("") }
    var out by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    UtilCard("URL encode/decode", "Проценты-кодирование.") {
        OutlinedTextField(src, { src = it; err = "" }, label = { Text("Текст или %D0%BF...") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { out = DeterministicUtils.urlEncode(src); err = "" }, modifier = Modifier.weight(1f), enabled = src.isNotEmpty()) { Text("Закодировать") }
            OutlinedButton(onClick = {
                when (val r = DeterministicUtils.urlDecode(src)) {
                    is DeterministicUtils.UrlResult.Ok -> { out = r.text; err = "" }
                    is DeterministicUtils.UrlResult.Err -> { out = ""; err = r.message }
                }
            }, modifier = Modifier.weight(1f), enabled = src.isNotEmpty()) { Text("Декодировать") }
        }
        if (out.isNotBlank()) {
            Text(out.take(4000), style = MaterialTheme.typography.bodySmall)
            CopyRow(out, onCopy)
        }
        if (err.isNotBlank()) Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatsCard() {
    var src by remember { mutableStateOf("") }
    UtilCard("Статистика текста", "Символы, слова, строки, время чтения.") {
        OutlinedTextField(src, { src = it }, label = { Text("Вставь текст") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6)
        Spacer(Modifier.height(8.dp))
        if (src.isNotEmpty()) {
            val s = DeterministicUtils.textStats(src)
            Text("Символов: ${s.chars} (без пробелов: ${s.charsNoSpaces})")
            Text("Слов: ${s.words} • Предложений: ${s.sentences} • Строк: ${s.lines}")
            Text("Чтение: ~${"%.1f".format(java.util.Locale.US, s.readingMin)} мин (200 слов/мин)")
        }
    }
}

@Composable
private fun UuidCard(onCopy: (String) -> Unit) {
    var out by remember { mutableStateOf(DeterministicUtils.newUuid()) }
    UtilCard("UUID генератор", "Случайный идентификатор.") {
        Text(out, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { out = DeterministicUtils.newUuid() }, modifier = Modifier.weight(1f)) { Text("Новый") }
            OutlinedButton(onClick = { onCopy(out) }, modifier = Modifier.weight(1f)) { Text("Копия") }
        }
    }
}
