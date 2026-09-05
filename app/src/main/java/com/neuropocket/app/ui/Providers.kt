package com.neuropocket.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.neuropocket.app.AppViewModel
import com.neuropocket.app.data.AiProvider
import com.neuropocket.app.data.ProviderPresets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(vm: AppViewModel, onBack: (() -> Unit)? = null) {
    var editing by remember { mutableStateOf<AiProvider?>(null) }
    var showCatalog by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Провайдеры и API") },
            navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                }
            },
            actions = {
                IconButton(onClick = { showCatalog = !showCatalog }) { Icon(Icons.Default.Add, contentDescription = "Добавить") }
            })
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Куда идут запросы чата, агента и ленты:", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary)
            }
            item {
                EngineChoiceCard(
                    title = "На телефоне (GGUF)",
                    sub = if (vm.nativeLoaded) "llama.cpp • модель в RAM" else "модель не загружена — будет Mock",
                    selected = vm.activeProviderId == "local",
                    onClick = { vm.selectProvider("local") }
                )
            }
            item {
                EngineChoiceCard(
                    title = "Заглушка Mock",
                    sub = "Шаблонные ответы без сети и без модели",
                    selected = vm.activeProviderId == "mock",
                    onClick = { vm.selectProvider("mock") }
                )
            }
            items(vm.providers, key = { it.id }) { p ->
                val sel = vm.activeProviderId == p.id
                ElevatedCard(
                    onClick = { vm.selectProvider(p.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = sel, onClick = { vm.selectProvider(p.id) })
                            Column(Modifier.weight(1f)) {
                                Text(p.name + if (sel) " • активен" else "", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (p.kind == "pollinations") "Pollinations • ${p.model.ifBlank { "openai" }}"
                                    else "${p.baseUrl} • ${p.model.ifBlank { "модель?" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            if (!p.enabled) AssistChip(onClick = {}, label = { Text("выкл") })
                        }
                        vm.provStatus[p.id]?.let { st ->
                            Text(st, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary)
                        }
                        val fetched = vm.provModels[p.id].orEmpty()
                        if (fetched.isNotEmpty()) {
                            Text("С сервера (${fetched.size}): ${fetched.take(5).joinToString(", ")}${if (fetched.size > 5) "…" else ""}",
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.testProvider(p.id) }, enabled = !vm.provBusy) {
                                Text("Проверить")
                            }
                            OutlinedButton(onClick = { editing = p }) { Text("Изменить") }
                            if (fetched.isNotEmpty()) {
                                OutlinedButton(onClick = {
                                    vm.updateProvider(p.copy(model = fetched.first()))
                                }) { Text("Взять 1-ю") }
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { vm.toggleProvider(p.id) }) {
                                Icon(if (p.enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Вкл/выкл")
                            }
                            IconButton(onClick = { vm.deleteProvider(p.id) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Удалить")
                            }
                        }
                    }
                }
            }
            if (showCatalog) {
                item { Text("ПК в локальной сети (подставь IP своего ПК):", style = MaterialTheme.typography.titleMedium) }
                items(ProviderPresets.local) { pr ->
                    PresetCard(pr.name, pr.descRu, false) { vm.addPreset(pr) }
                }
                item { Text("Облачные API (ключ — твой, хранится только на телефоне):", style = MaterialTheme.typography.titleMedium) }
                items(ProviderPresets.cloud) { pr ->
                    PresetCard(pr.name, pr.descRu, pr.needKey) { vm.addPreset(pr) }
                }
                item {
                    OutlinedButton(onClick = {
                        editing = AiProvider(name = "Свой API", kind = "openai", baseUrl = "https://", model = "")
                    }, modifier = Modifier.fillMaxWidth()) { Text("+ Полностью свой endpoint") }
                    Text("Ключи никуда не отправляются кроме указанного сервера. Pollinations — единственный без ключа.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }

    editing?.let { p0 ->
        ProviderEditor(p = p0, models = vm.provModels[p0.id].orEmpty(),
            onDismiss = { editing = null },
            onSave = {
                if (vm.providers.any { e -> e.id == it.id }) vm.updateProvider(it) else vm.addProvider(it)
                editing = null
            })
    }
}

@Composable
private fun EngineChoiceCard(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun PresetCard(name: String, desc: String, needKey: Boolean, onAdd: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (needKey) AssistChip(onClick = {}, label = { Text("нужен ключ") })
            }
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(6.dp))
            Button(onClick = onAdd) { Text("Добавить") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderEditor(
    p: AiProvider,
    models: List<String>,
    onDismiss: () -> Unit,
    onSave: (AiProvider) -> Unit
) {
    var name by remember(p.id) { mutableStateOf(p.name) }
    var base by remember(p.id) { mutableStateOf(p.baseUrl) }
    var key by remember(p.id) { mutableStateOf(p.apiKey) }
    var model by remember(p.id) { mutableStateOf(p.model) }
    var showKey by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Провайдер") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(name, { name = it }, label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                if (p.kind == "openai") {
                    item {
                        OutlinedTextField(base, { base = it }, label = { Text("Base URL (…/v1)") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                } else {
                    item { Text("Pollinations: URL и ключ не нужны.", style = MaterialTheme.typography.bodySmall) }
                }
                item {
                    OutlinedTextField(key, { key = it }, label = { Text("API-ключ (если нужен)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(if (showKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null)
                            }
                        })
                }
                item {
                    if (models.isNotEmpty()) {
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                            OutlinedTextField(model, { model = it }, label = { Text("Модель") },
                                modifier = Modifier.fillMaxWidth().menuAnchor(), singleLine = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                models.take(50).forEach { m ->
                                    DropdownMenuItem(text = { Text(m) }, onClick = { model = m; expanded = false })
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(model, { model = it }, label = { Text("Модель (или «Проверить» для списка)") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(p.copy(name = name.ifBlank { "API" }, baseUrl = base.trim(), apiKey = key.trim(), model = model.trim())) }) {
                Text("Сохранить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
