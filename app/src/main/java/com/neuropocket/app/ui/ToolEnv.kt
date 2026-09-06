package com.neuropocket.app.ui

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.neuropocket.app.AppViewModel
import com.neuropocket.app.core.AnalyzerWorkflow
import com.neuropocket.app.core.CapabilityDisclosure
import com.neuropocket.app.core.ImproverWorkflow
import com.neuropocket.app.core.SummarizerWorkflow
import com.neuropocket.app.core.ToolChunking
import com.neuropocket.app.core.TranslatorWorkflow
import com.neuropocket.app.core.VibeCodeWorkflow
import com.neuropocket.app.data.ToolCatalog

/** Отдельная среда текстового инструмента со своей историей (Phase B: настоящие workflows). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ToolEnvScreen(vm: AppViewModel, toolId: String, onBack: () -> Unit, onDiscuss: (String) -> Unit) {
    val def = ToolCatalog.byId(toolId)
    if (def == null) {
        Scaffold(topBar = {
            TopAppBar(title = { Text("Инструмент") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") }
            })
        }) { pad ->
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Нет такого инструмента.")
            }
        }
        return
    }
    var input by remember(toolId) { mutableStateOf("") }
    var langFrom by remember(toolId) { mutableStateOf("Русский") }
    var langTo by remember(toolId) { mutableStateOf("Английский") }
    var preserveFmt by remember(toolId) { mutableStateOf(true) }
    var formality by remember(toolId) { mutableStateOf("neutral") }
    var glossary by remember(toolId) { mutableStateOf("") }
    var sumMode by remember(toolId) { mutableStateOf("short") }
    var impMode by remember(toolId) { mutableStateOf("natural") }
    var vibeLang by remember(toolId) { mutableStateOf("") }
    var vibeFramework by remember(toolId) { mutableStateOf("") }
    var vibeContext by remember(toolId) { mutableStateOf("") }
    val ctx = LocalContext.current
    val busy = vm.toolBusyId == toolId
    val hist = vm.toolHistory(toolId)

    // Входящий шаринг/импорт: подхватить текст из других приложений.
    LaunchedEffect(toolId) {
        try {
            vm.consumeToolShare()?.let { shared ->
                if (shared.isNotBlank() && input.isBlank()) input = shared
            }
        } catch (_: Exception) { }
    }
    val textPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val t = vm.importTextFile(uri)
            if (t != null) {
                input = t
                vm.fireRoute("tool:$toolId")
            }
        } catch (_: Exception) { }
    }

    fun doRun() {
        when (toolId) {
            "translator" -> vm.runTool(
                toolId, input, langFrom, langTo,
                preserveFormatting = preserveFmt, formality = formality, glossary = glossary
            )
            "summarizer" -> vm.runTool(toolId, input, mode = sumMode)
            "improver" -> vm.runTool(toolId, input, mode = impMode)
            "vibecode" -> vm.runTool(toolId, input, extra = vibeContext, vibeLang = vibeLang, vibeFramework = vibeFramework)
            else -> vm.runTool(toolId, input)
        }
    }

    fun shareText(text: String, title: String) {
        try {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            ctx.startActivity(Intent.createChooser(i, title))
        } catch (_: Exception) { }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(def.title) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") } },
            actions = { IconButton(onClick = { vm.clearTool(toolId) }) { Icon(Icons.Default.Delete, contentDescription = "Очистить историю") } }
        )
    }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            def.hint, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            "Движок: ${vm.engineLabel()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        if (vm.activeProviderId == "mock" ||
                            (vm.activeProviderId == "local" && !vm.nativeLoaded)
                        ) {
                            Text(
                                "Mock / template fallback — загрузи GGUF или выбери провайдера для полного качества.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        when (toolId) {
                            "translator" -> {
                                LangRow(
                                    from = langFrom, to = langTo,
                                    onFrom = { langFrom = it }, onTo = { langTo = it },
                                    onSwap = {
                                        val f = langFrom
                                        langFrom = langTo
                                        langTo = f
                                    }
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = preserveFmt,
                                        onClick = { preserveFmt = !preserveFmt },
                                        label = { Text("Форматирование") })
                                    listOf("formal" to "Формально", "neutral" to "Нейтрально", "informal" to "Неформально").forEach { (v, t) ->
                                        FilterChip(selected = formality == v, onClick = { formality = v }, label = { Text(t) })
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    glossary, { glossary = it },
                                    label = { Text("Глоссарий (необязательно): термин = перевод") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true, maxLines = 1
                                )
                                Spacer(Modifier.height(8.dp))
                                if (ToolChunking.needsChunking(input)) {
                                    val n = ToolCatalog.splitForTool(toolId, input).size
                                    Text(
                                        "Длинный текст: будет переведён по частям ($n), собран в исходном порядке.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                            "summarizer" -> {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        "short" to "Кратко", "detailed" to "Подробно",
                                        "keypoints" to "Тезисы", "actions" to "Действия",
                                        "timeline" to "Timeline"
                                    ).forEach { (v, t) ->
                                        FilterChip(selected = sumMode == v, onClick = { sumMode = v }, label = { Text(t) })
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                if (ToolChunking.needsChunking(input, 3000)) {
                                    val n = ToolCatalog.splitForTool(toolId, input).size
                                    Text(
                                        "Длинный текст: $n частей → локальные саммари → общий синтез (${SummarizerWorkflow.modeTitle(sumMode)}).",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            "improver" -> {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        "grammar" to "Грамматика", "natural" to "Естественно",
                                        "professional" to "Деловой", "concise" to "Короче",
                                        "expand" to "Развернуть", "clearer" to "Понятнее",
                                        "tone" to "Сохранить тон"
                                    ).forEach { (v, t) ->
                                        FilterChip(selected = impMode == v, onClick = { impMode = v }, label = { Text(t) })
                                    }
                                }
                            }
                            "vibecode" -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        vibeLang, { vibeLang = it }, label = { Text("Язык") },
                                        modifier = Modifier.weight(1f), singleLine = true
                                    )
                                    OutlinedTextField(
                                        vibeFramework, { vibeFramework = it }, label = { Text("Фреймворк") },
                                        modifier = Modifier.weight(1f), singleLine = true
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    vibeContext, { vibeContext = it },
                                    label = { Text("Контекст/существующий код (необязательно)") },
                                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    VibeCodeWorkflow.NOT_EXECUTED,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            input, { input = it }, label = { Text(def.inputLabel) },
                            modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 10
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { doRun() },
                                enabled = !busy && input.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (busy) "Думаю…" else "Выполнить")
                            }
                            if (toolId == "summarizer" || toolId == "vibecode") {
                                OutlinedButton(
                                    onClick = { textPicker.launch("text/*") },
                                    enabled = !busy
                                ) { Text("Файл") }
                            }
                        }
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
            if (hist.isEmpty()) {
                item { Text("История пуста — первый запуск выше.", color = MaterialTheme.colorScheme.secondary) }
            }
            items(hist.reversed(), key = { it.id }) { r ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        // Мета: языки/режим/движок/время + честность.
                        val meta = buildString {
                            if (r.sourceLang.isNotBlank() || r.targetLang.isNotBlank()) {
                                append("${r.sourceLang.ifBlank { "авто" }} → ${r.targetLang.ifBlank { "?" }}  •  ")
                            }
                            if (r.mode.isNotBlank()) append("${r.mode}  •  ")
                            if (r.engine.isNotBlank()) append("${r.engine}  •  ")
                            append(timeAgo(r.ts))
                        }
                        Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        if (r.mockFallback || CapabilityDisclosure.isMockOutput(r.output)) {
                            Text(
                                "Mock / template fallback",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        when (toolId) {
                            "translator" -> {
                                Text("Оригинал:", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    r.input.take(2000), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                Text("Перевод:", style = MaterialTheme.typography.labelMedium)
                                MarkdownText(r.output, fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                            }
                            "improver" -> {
                                val (improved, changes) = ImproverWorkflow.splitImproved(r.output)
                                Text("Оригинал:", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    r.input.take(2000), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                Text("Улучшенный:", style = MaterialTheme.typography.labelMedium)
                                MarkdownText(
                                    improved.ifBlank { r.output },
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                                )
                                if (changes.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Изменения: $changes",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            "detector" -> {
                                val parsed = AnalyzerWorkflow.parse(r.output)
                                if (parsed != null) {
                                    AnalyzerCard("Язык", parsed.language)
                                    AnalyzerCard("Тон", parsed.tone)
                                    AnalyzerCard("Настроение", parsed.sentiment)
                                    AnalyzerCard("Намерение", parsed.intent)
                                    AnalyzerCard("Читаемость", parsed.readability)
                                    AnalyzerCard("Проблемы", parsed.issues)
                                    AnalyzerCard("Уверенность", parsed.confidence)
                                    if (parsed.notes.isNotBlank()) AnalyzerCard("Заметки", parsed.notes)
                                } else {
                                    MarkdownText(r.output, fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                                    Text(
                                        "Структурированный разбор не удался — показан сырой ответ модели.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            "vibecode" -> {
                                val v = VibeCodeWorkflow.parse(r.output)
                                Text(
                                    VibeCodeWorkflow.NOT_EXECUTED,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(Modifier.height(4.dp))
                                if (v.files.isEmpty()) {
                                    MarkdownText(r.output, fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                                } else {
                                    v.files.forEach { f ->
                                        ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                            Column(Modifier.padding(8.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        f.name, style = MaterialTheme.typography.labelMedium,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        f.language, style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.secondary
                                                    )
                                                }
                                                Text(
                                                    f.code.take(3000),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                Row {
                                                    TextButton(onClick = {
                                                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                        cm.setPrimaryClip(ClipData.newPlainText("np", f.code))
                                                    }) { Text("Копия файла") }
                                                }
                                            }
                                        }
                                    }
                                    if (v.explanation.isNotBlank()) {
                                        Text("Объяснение:", style = MaterialTheme.typography.labelMedium)
                                        Text(v.explanation, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (v.runInstructions.isNotBlank()) {
                                        Text("Запуск:", style = MaterialTheme.typography.labelMedium)
                                        Text(v.runInstructions, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (v.warnings.isNotBlank()) {
                                        Text("Риски:", style = MaterialTheme.typography.labelMedium)
                                        Text(v.warnings, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = {
                                        val all = if (v.files.isNotEmpty()) {
                                            v.files.joinToString("\n\n") { "=== ${it.name} ===\n${it.code}" } +
                                                "\n\nОбъяснение: ${v.explanation}\nЗапуск: ${v.runInstructions}\nРиски: ${v.warnings}\n${VibeCodeWorkflow.NOT_EXECUTED}"
                                        } else r.output
                                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("np", all))
                                    }) { Text("Копия всего") }
                                    TextButton(onClick = {
                                        val all = if (v.files.isNotEmpty()) {
                                            v.files.joinToString("\n\n") { "=== ${it.name} ===\n${it.code}" }
                                        } else r.output
                                        shareText(all, "Поделиться кодом")
                                    }) { Text("Поделиться") }
                                }
                            }
                            else -> {
                                Text(
                                    r.input.take(500), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary, maxLines = 4
                                )
                                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                MarkdownText(r.output, fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(timeAgo(r.ts), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextButton(onClick = {
                                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("np", r.output))
                            }) { Text("Копия") }
                            TextButton(onClick = { shareText(r.output, "Поделиться результатом") }) { Text("Поделиться") }
                            TextButton(onClick = {
                                try {
                                    vm.saveNote("${def.title} ${timeAgo(r.ts)}", "# ${def.title}\n\nВход:\n${r.input.take(4000)}\n\nРезультат:\n${r.output.take(8000)}")
                                } catch (_: Exception) { }
                            }) { Text("Сохранить") }
                            TextButton(onClick = {
                                onDiscuss("${def.title}. Вход: ${r.input.take(500)}\nРезультат: ${r.output.take(1200)}\nРазберём подробнее.")
                            }) { Text("В чат") }
                            TextButton(onClick = {
                                input = r.input
                                doRun()
                            }) { Text("Retry") }
                            TextButton(onClick = { vm.deleteToolRun(toolId, r.id) }) { Text("Удалить") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyzerCard(label: String, value: String) {
    if (value.isBlank()) return
    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Column(Modifier.padding(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LangRow(
    from: String, to: String,
    onFrom: (String) -> Unit, onTo: (String) -> Unit, onSwap: () -> Unit
) {
    var expFrom by remember { mutableStateOf(false) }
    var expTo by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ExposedDropdownMenuBox(expanded = expFrom, onExpandedChange = { expFrom = it }, modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                from, {}, readOnly = true, label = { Text("С языка") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expFrom) },
                modifier = Modifier.menuAnchor().fillMaxWidth(), singleLine = true
            )
            ExposedDropdownMenu(expanded = expFrom, onDismissRequest = { expFrom = false }) {
                TranslatorWorkflow.SUPPORTED_LANGS.forEach { l ->
                    DropdownMenuItem(text = { Text(l) }, onClick = { onFrom(l); expFrom = false })
                }
            }
        }
        IconButton(onClick = onSwap) { Icon(Icons.Filled.SwapHoriz, contentDescription = "Поменять языки") }
        ExposedDropdownMenuBox(expanded = expTo, onExpandedChange = { expTo = it }, modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                to, {}, readOnly = true, label = { Text("На язык") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expTo) },
                modifier = Modifier.menuAnchor().fillMaxWidth(), singleLine = true
            )
            ExposedDropdownMenu(expanded = expTo, onDismissRequest = { expTo = false }) {
                TranslatorWorkflow.SUPPORTED_LANGS.filter { it != "Авто" }.forEach { l ->
                    DropdownMenuItem(text = { Text(l) }, onClick = { onTo(l); expTo = false })
                }
            }
        }
    }
}
