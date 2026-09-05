package com.neuropocket.app.ui

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
import com.neuropocket.app.data.ToolCatalog

/** Отдельная среда текстового инструмента со своей историей. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolEnvScreen(vm: AppViewModel, toolId: String, onBack: () -> Unit, onDiscuss: (String) -> Unit) {
    val def = ToolCatalog.byId(toolId)
    if (def == null) {
        Scaffold(topBar = { TopAppBar(title = { Text("Инструмент") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") }
        }) }) { pad ->
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Нет такого инструмента.")
            }
        }
        return
    }
    var input by remember(toolId) { mutableStateOf("") }
    var langFrom by remember(toolId) { mutableStateOf("русский") }
    var langTo by remember(toolId) { mutableStateOf("английский") }
    val ctx = LocalContext.current
    val busy = vm.toolBusyId == toolId
    val hist = vm.toolHistory(toolId)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(def.title) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") } },
            actions = { IconButton(onClick = { vm.clearTool(toolId) }) { Icon(Icons.Default.Delete, contentDescription = "Очистить историю") } }
        )
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(def.hint, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary)
                        Text("Движок: ${vm.engineLabel()}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(8.dp))
                        if (def.withLangs) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(langFrom, { langFrom = it }, label = { Text("С языка") },
                                    modifier = Modifier.weight(1f), singleLine = true)
                                OutlinedTextField(langTo, { langTo = it }, label = { Text("На язык") },
                                    modifier = Modifier.weight(1f), singleLine = true)
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        OutlinedTextField(input, { input = it }, label = { Text(def.inputLabel) },
                            modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { vm.runTool(toolId, input, langFrom, langTo) },
                            enabled = !busy && input.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Text(if (busy) "Думаю…" else "Выполнить")
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
                        Text(r.input.take(300), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary, maxLines = 4)
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        MarkdownText(r.output, fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                        Spacer(Modifier.height(4.dp))
                        Text(timeAgo(r.ts), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary)
                        Row {
                            TextButton(onClick = {
                                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("np", r.output))
                            }) { Text("Копия") }
                            TextButton(onClick = {
                                onDiscuss("${def.title}. Вход: ${r.input.take(500)}\nРезультат: ${r.output.take(1200)}\nРазберём подробнее.")
                            }) { Text("В чат") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { vm.deleteToolRun(toolId, r.id) }) { Text("Удалить") }
                        }
                    }
                }
            }
        }
    }
}
