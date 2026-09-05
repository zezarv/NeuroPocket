package com.neuropocket.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.neuropocket.app.AppViewModel
import com.neuropocket.app.data.NpLog

/** Диагностика: устройство, состояние, лог, краш — всё для отладки. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var logText by remember { mutableStateOf("") }
    var crashText by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        logText = NpLog.deviceInfo(ctx.applicationContext as android.app.Application) +
            "\n" + vm.diagText() + "\n--- LOG ---\n" + NpLog.dump()
        crashText = try {
            val f = NpLog.crashFile(ctx.applicationContext as android.app.Application)
            if (f.exists()) f.readText().take(4000) else null
        } catch (_: Exception) { null }
    }
    LaunchedEffect(Unit) { refresh() }

    fun share(text: String, name: String) {
        try {
            val f = java.io.File(ctx.cacheDir, name)
            f.writeText(text)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                ctx, ctx.packageName + ".fileprovider", f)
            val sh = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(android.content.Intent.createChooser(sh, "Диагностика"))
        } catch (_: Exception) { }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Диагностика") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") } },
            actions = {
                IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, contentDescription = "Обновить") }
            })
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { share(logText, "neuropocket-log.txt") }, modifier = Modifier.weight(1f)) {
                        Text("Отправить лог")
                    }
                    OutlinedButton(onClick = {
                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("np-log", logText))
                    }, modifier = Modifier.weight(1f)) { Text("Копия") }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { NpLog.clear(); refresh() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Очистить лог")
                }
            }
            if (crashText != null) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Последнее падение:", color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleSmall)
                            Text(crashText!!, fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { share(crashText!!, "neuropocket-crash.txt") },
                                    modifier = Modifier.weight(1f)) { Text("Отправить краш") }
                                OutlinedButton(onClick = {
                                    try { NpLog.crashFile(ctx.applicationContext as android.app.Application).delete() } catch (_: Exception) { }
                                    refresh()
                                }, modifier = Modifier.weight(1f)) { Text("Убрать") }
                            }
                        }
                    }
                }
            }
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Text(logText, fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}
