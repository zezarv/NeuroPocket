package com.neuropocket.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.neuropocket.app.AppViewModel
import com.neuropocket.app.data.ModelCatalog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialScreen(vm: AppViewModel, onOpenPersona: (String) -> Unit) {
    var composer by remember { mutableStateOf("") }
    var tagFilter by remember { mutableStateOf("Все") }
    var authorMenu by remember { mutableStateOf(false) }
    var authorId by remember { mutableStateOf(vm.personas.firstOrNull()?.id ?: "") }
    val author = vm.personas.find { it.id == authorId } ?: vm.personas.firstOrNull()

    val tagFreq = remember(vm.posts) {
        vm.posts.flatMap { com.neuropocket.app.data.extractTags(it.text) }
            .groupingBy { it }.eachCount().toList()
            .sortedByDescending { it.second }.take(12)
    }
    val shown = remember(vm.posts, tagFilter) {
        val all = vm.posts.sortedByDescending { it.ts }
        if (tagFilter == "Все") all else all.filter { tagFilter in com.neuropocket.app.data.extractTags(it.text) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Лента") }, actions = {
        IconButton(onClick = { vm.clearPosts() }) { Icon(Icons.Default.Delete, contentDescription = "Очистить всё") }
    }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (author != null) AvatarView(author.avatarPath, author.avatarEmoji, 40.dp)
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.weight(1f)) {
                                OutlinedButton(onClick = { authorMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(author?.name ?: "Нет персон")
                                }
                                DropdownMenu(expanded = authorMenu, onDismissRequest = { authorMenu = false }) {
                                    vm.personas.forEach { per ->
                                        DropdownMenuItem(text = { Text(per.name) }, onClick = {
                                            authorId = per.id; authorMenu = false
                                        })
                                    }
                                }
                            }
                            Button(onClick = { if (author != null) vm.aiPost(author.id, 2) }) { Text("ИИ-пост") }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(composer, { composer = it },
                            label = { Text("Написать пост… (#хештеги сами подсветятся)") },
                            modifier = Modifier.fillMaxWidth(), minLines = 2)
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = {
                            if (author != null && composer.isNotBlank()) {
                                vm.addPost(author.id, composer); composer = ""
                            }
                        }, enabled = composer.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Text("Опубликовать")
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Автопостинг:", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 8.dp))
                    listOf(0 to "выкл", 6 to "6ч", 12 to "12ч", 24 to "24ч").forEach { (h, nm) ->
                        FilterChip(selected = vm.autopostHours == h, onClick = { vm.applyAutopost(h) },
                            label = { Text(nm) })
                        Spacer(Modifier.width(4.dp))
                    }
                }
                Text("Фон: случайная персона, движок — твой провайдер или шаблоны.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = tagFilter == "Все", onClick = { tagFilter = "Все" }, label = { Text("Все") })
                    tagFreq.forEach { (t, n) ->
                        FilterChip(selected = tagFilter == t, onClick = { tagFilter = if (tagFilter == t) "Все" else t },
                            label = { Text("$t $n") })
                    }
                }
            }
            items(shown, key = { it.id }) { p ->
                val a = vm.personas.find { it.id == p.authorId }
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(enabled = a != null) { a?.let { onOpenPersona(it.id) } }) {
                            if (a != null) AvatarView(a.avatarPath, a.avatarEmoji, 44.dp)
                            else Text("\uD83D\uDE00", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a?.name ?: "Удалённая персона")
                                Text(timeAgo(p.ts), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary)
                            }
                            if (p.aiMade) AssistChip(onClick = {}, label = { Text("ИИ") })
                        }
                        Spacer(Modifier.height(6.dp))
                        HashtagText(p.text, active = tagFilter) { tagFilter = it }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.toggleLike(p.id) }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    if (p.liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Лайк",
                                    tint = if (p.liked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                                )
                            }
                            Text("${p.likes}")
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { vm.deletePost(p.id) }) { Text("Удалить") }
                        }
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        if (vm.posts.isEmpty()) "Пока пусто. Выбери персону и нажми «ИИ-пост» — или напиши свой."
                        else "С тегом $tagFilter ничего нет.",
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun HashtagText(text: String, active: String, onTag: (String) -> Unit) {
    val tags = remember(text) { com.neuropocket.app.data.extractTags(text) }
    if (tags.isEmpty()) {
        Text(text)
        return
    }
    val annotated = remember(text, active) {
        val b = androidx.compose.ui.text.AnnotatedString.Builder()
        var i = 0
        val re = Regex("#[\\p{L}\\p{N}_]+")
        for (m in re.findAll(text)) {
            if (m.range.first > i) b.append(text.substring(i, m.range.first))
            b.pushStringAnnotation("tag", m.value.lowercase())
            b.pushStyle(
                androidx.compose.ui.text.SpanStyle(
                    color = androidx.compose.ui.graphics.Color(0xFFD9A441),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
            )
            b.append(m.value)
            b.pop()
            b.pop()
            i = m.range.last + 1
        }
        if (i < text.length) b.append(text.substring(i))
        b.toAnnotatedString()
    }
    androidx.compose.foundation.text.ClickableText(
        text = annotated,
        onClick = { off ->
            annotated.getStringAnnotations("tag", off, off).firstOrNull()?.let { onTag(it.item) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(vm: AppViewModel, onOpenProviders: () -> Unit = {}) {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val f = ctx.copyUriToModels(uri, "imported")
        if (f != null) vm.scanModels()
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Модели") }, actions = {
        IconButton(onClick = { vm.scanModels() }) { Icon(Icons.Default.Refresh, contentDescription = "Сканировать") }
    }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Движок: ${vm.engineLabel()}", style = MaterialTheme.typography.titleSmall)
                        Text(vm.nativeInfo, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.toggleEngine() }, modifier = Modifier.weight(1f)) {
                                Text(if (vm.useNative) "На Mock" else "На Native")
                            }
                            OutlinedButton(onClick = { vm.applyAutoFallback(!vm.autoFallback) }, modifier = Modifier.weight(1f)) {
                                Text(if (vm.autoFallback) "Запасной ✓" else "Запасной ✗")
                            }
                        }
                        Text("GPU (Vulkan/Adreno): " + if (vm.gpuSupported()) "доступен" else "пока CPU (Vulkan SDK будет позже)",
                            style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "CPU", 20 to "GPU-20", 60 to "GPU-60", 999 to "GPU-все").forEach { (v, nm) ->
                                FilterChip(selected = vm.gpuLayers == v, onClick = { vm.applyGpuLayers(v) }, label = { Text(nm) })
                            }
                        }
                        Text("Слоёв на GPU больше — быстрее, но прожорливее. Применится при след. загрузке. Сравни бенчмарком ниже.",
                            style = MaterialTheme.typography.labelSmall)
                        if (vm.nativeLoaded) {
                            OutlinedButton(onClick = { vm.unloadNative() }, modifier = Modifier.fillMaxWidth()) { Text("Выгрузить из RAM") }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Источник ответов:", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = vm.activeProviderId == "local",
                                onClick = { vm.selectProvider("local") }, label = { Text("Телефон") })
                            FilterChip(selected = vm.activeProviderId == "mock",
                                onClick = { vm.selectProvider("mock") }, label = { Text("Mock") })
                            vm.providers.filter { it.enabled }.forEach { p ->
                                FilterChip(selected = vm.activeProviderId == p.id,
                                    onClick = { vm.selectProvider(p.id) }, label = { Text(p.name.take(12)) })
                            }
                        }
                        OutlinedButton(onClick = onOpenProviders, modifier = Modifier.fillMaxWidth()) {
                            Text("ПК и API (LM Studio, Ollama, ключи) →")
                        }
                    }
                }
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Бенчмарк (токены/с на этом телефоне)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (vm.benchResult.isBlank()) "Замеряет разбор промпта и генерацию 32 токенов загруженной моделью."
                            else vm.benchResult,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = { vm.runBench() }, enabled = vm.nativeLoaded && !vm.benchRunning,
                            modifier = Modifier.fillMaxWidth()) {
                            Text(if (vm.benchRunning) "Замер…" else "Запустить замер")
                        }
                        if (vm.benchRunning) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (vm.benchHistory.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("История замеров:", style = MaterialTheme.typography.labelMedium)
                            vm.benchHistory.reversed().forEach { h ->
                                Text("• $h", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
                Text("Файлы .gguf в: Android/data/.../files/models/", style = MaterialTheme.typography.labelSmall)
                Text("Найдено: ${vm.modelFiles.size}", style = MaterialTheme.typography.bodyMedium)
                vm.modelFiles.forEach { f ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text("• ${f.name} (${f.length() / 1048576} МБ)", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.loadFileToRam(f) }) { Text("В RAM") }
                                OutlinedButton(onClick = { vm.loadFileToRam(f, 1024) }) { Text("RAM 1k") }
                            }
                            Text("По умолч. ctx ${vm.ctxSize}, потоки ${if (vm.threads == 0) "авто" else "${vm.threads}"} — меняется в настройках.",
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { picker.launch("*/*") }, modifier = Modifier.weight(1f)) { Text("Импорт GGUF") }
                    OutlinedButton(onClick = { vm.scanModels() }, modifier = Modifier.weight(1f)) { Text("Обновить") }
                }
                Text("Активна: ${vm.activeModelId ?: "не выбрана (Mock)"}", style = MaterialTheme.typography.labelMedium)
                Text("Совет: для S24 Ultra начни с Llama 3.2 3B Q4, контекст 2048. 7B — только если свободно 6+ ГБ.", style = MaterialTheme.typography.labelSmall)
                HorizontalDivider()
                Text("Каталог для телефона (от мелких к большим + 18+ без цензуры):", style = MaterialTheme.typography.titleSmall)
            }
            items(ModelCatalog.models) { m ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(m.name, style = MaterialTheme.typography.titleSmall)
                        Text("${m.sizeLabel} • нужно ~${m.ramNeedGb} ГБ • ${m.fileName}", style = MaterialTheme.typography.labelSmall)
                        Text(m.descRu, style = MaterialTheme.typography.bodySmall)
                        if (m.nsfw) AssistChip(onClick = {}, label = { Text("18+ uncensored") })
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.downloadModel(m) }) { Text("Скачать") }
                            OutlinedButton(onClick = { vm.setActiveModel(m.id) }) { Text("Выбрать") }
                        }
                        DlRow(vm, m.fileName)
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("Whisper-модели (транскрибация, .bin):", style = MaterialTheme.typography.titleSmall)
            }
            items(ModelCatalog.whisperModels) { m ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(m.name, style = MaterialTheme.typography.titleSmall)
                        Text("${m.fileName}", style = MaterialTheme.typography.labelSmall)
                        Text(m.descRu, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = { vm.downloadModel(m) }) { Text("Скачать") }
                        DlRow(vm, m.fileName)
                    }
                }
            }
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Голоса (sherpa/piper, офлайн)", style = MaterialTheme.typography.titleSmall)
                        Text(vm.ttsInfo, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        if (vm.voiceDirs.isEmpty()) {
                            Text("Нет распакованных голосов.", style = MaterialTheme.typography.bodySmall)
                        }
                        vm.voiceDirs.forEach { v ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("• $v", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { vm.loadVoice(v) }) { Text("В RAM") }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.extractVoices() }, modifier = Modifier.weight(1f)) {
                                Text("Распаковать")
                            }
                            OutlinedButton(onClick = { vm.scanModels() }, modifier = Modifier.weight(1f)) {
                                Text("Обновить")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Каталог голосов + VAD:", style = MaterialTheme.typography.titleSmall)
            }
            items(ModelCatalog.voiceModels) { m ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(m.name, style = MaterialTheme.typography.titleSmall)
                        Text("${m.fileName}", style = MaterialTheme.typography.labelSmall)
                        Text(m.descRu, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = { vm.downloadModel(m) }) { Text("Скачать") }
                        DlRow(vm, m.fileName)
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("mmproj (зрение, порядок: vision-GGUF → mmproj):", style = MaterialTheme.typography.titleSmall)
                Text(vm.visionInfo, style = MaterialTheme.typography.bodySmall)
                vm.mmprojFiles.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("• ${f.name} (${f.length() / 1048576} МБ)", modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { vm.loadVisionToRam(f) }) { Text("Зрение в RAM") }
                    }
                }
            }
            items(ModelCatalog.mmprojModels) { m ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(m.name, style = MaterialTheme.typography.titleSmall)
                        Text("${m.fileName}", style = MaterialTheme.typography.labelSmall)
                        Text(m.descRu, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = { vm.downloadModel(m) }) { Text("Скачать") }
                        DlRow(vm, m.fileName)
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("Вектора (RAG по заметкам):", style = MaterialTheme.typography.titleSmall)
                Text(vm.embedInfo, style = MaterialTheme.typography.bodySmall)
                vm.modelFiles.filter {
                    val n = it.name.lowercase()
                    ("e5" in n || "embed" in n || "bge" in n || "nomic" in n) && "mmproj" !in n
                }.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("• ${f.name} (${f.length() / 1048576} МБ)", modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { vm.loadEmbedToRam(f) }) { Text("Вектора в RAM") }
                    }
                }
            }
            items(ModelCatalog.embedModels) { m ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(m.name, style = MaterialTheme.typography.titleSmall)
                        Text("${m.fileName}", style = MaterialTheme.typography.labelSmall)
                        Text(m.descRu, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = { vm.downloadModel(m) }) { Text("Скачать") }
                        DlRow(vm, m.fileName)
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("SD-модели (фото, .safetensors, ГБ):", style = MaterialTheme.typography.titleSmall)
            }
            items(ModelCatalog.sdModels) { m ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(m.name, style = MaterialTheme.typography.titleSmall)
                        Text("${m.fileName}", style = MaterialTheme.typography.labelSmall)
                        Text(m.descRu, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = { vm.downloadModel(m) }) { Text("Скачать (Wi-Fi)") }
                        DlRow(vm, m.fileName)
                    }
                }
            }
            item {
                Text(
                    "TAESD: " + if (vm.taesdFiles.isEmpty()) "нет (финал медленнее)"
                    else "найден, подхватится сам: " + (vm.taesdFiles.firstOrNull()?.name ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            items(ModelCatalog.taesdModels) { m ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(m.name, style = MaterialTheme.typography.titleSmall)
                        Text("${m.fileName}", style = MaterialTheme.typography.labelSmall)
                        Text(m.descRu, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = { vm.downloadModel(m) }) { Text("Скачать") }
                        DlRow(vm, m.fileName)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onOpenTab: (Int) -> Unit = {}, onOpenPersonas: () -> Unit = {}, onOpenProviders: () -> Unit = {}, onOpenDiag: () -> Unit = {}) {
    var query by remember { mutableStateOf("") }
    var bkKeys by remember { mutableStateOf(false) }
    var bkPass by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) vm.restoreBackup(uri, bkPass)
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Системные настройки") }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(query, { query = it }, label = { Text("Поиск по настройкам…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingRow(Icons.Default.Psychology, "Мозг чата",
                            "${vm.engineLabel()} • ${vm.activeModelId ?: "модель не выбрана"}",
                            query, onClick = { onOpenTab(4) })
                        SettingRow(Icons.Default.RecordVoiceOver, "Голос и речь",
                            "Системные STT/TTS + whisper ${if (vm.whisperLoaded) "в RAM" else "не загружен"}",
                            query, onClick = { onOpenTab(4) })
                        SettingRow(Icons.Default.Palette, "Генерация изображений",
                            if (vm.sdLoaded) "SD в RAM • галерея ${vm.gallery.size}" else "SD модель не загружена",
                            query, onClick = { onOpenTab(4) })
                        SettingRow(Icons.Default.Person, "Персоны",
                            "${vm.personas.size} шт • активна ${vm.activePersona?.name ?: "—"}",
                            query, onClick = onOpenPersonas)
                        SettingRow(Icons.Default.BugReport, "Диагностика",
                            "Логи, краши, состояние",
                            query, onClick = onOpenDiag)
                        SettingRow(Icons.Default.Cloud, "Провайдеры и API",
                            "${vm.providers.size} шт • ${vm.engineLabel()}",
                            query, showDivider = false, onClick = onOpenProviders)
                    }
                }
            }
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Приложение", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Тема", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = vm.theme == "light", onClick = { vm.applyTheme("light") }, label = { Text("Светлая") })
                            FilterChip(selected = vm.theme == "dark", onClick = { vm.applyTheme("dark") }, label = { Text("Тёмная") })
                            FilterChip(selected = vm.theme == "auto", onClick = { vm.applyTheme("auto") }, label = { Text("Авто") })
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Акцент", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("#D9A441" to "Золото", "#6750A4" to "Фиолет", "#0061A4" to "Синий", "#006D3B" to "Зелень", "#8C1D18" to "Красный").forEach { (hex, nm) ->
                                FilterChip(selected = vm.accent.equals(hex, true), onClick = { vm.applyAccent(hex) }, label = { Text(nm) })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Не гасить экран", style = MaterialTheme.typography.bodyMedium)
                                Text("Во время чата и генерации", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary)
                            }
                            Switch(checked = vm.keepScreenOn, onCheckedChange = { vm.applyKeepScreenOn(it) })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Качать только по Wi-Fi", style = MaterialTheme.typography.bodyMedium)
                                Text("Мобильный трафик не трогаем", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary)
                            }
                            Switch(checked = vm.wifiOnly, onCheckedChange = { vm.applyWifiOnly(it) })
                        }
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text("Автожизнь моделей", style = MaterialTheme.typography.labelMedium)
                        AutoRow("Автозагрузка в чате", "Выбранная GGUF сама в RAM при входе",
                            vm.autoloadChat, { vm.applyAutoloadChat(it) })
                        AutoRow("Автозагрузка whisper", "Для транскрибатора и голосового чата",
                            vm.autoloadWhisper, { vm.applyAutoloadWhisper(it) })
                        AutoRow("Автозагрузка SD", "Тяжёлая (ГБ) — по умолчанию выкл.",
                            vm.autoloadSd, { vm.applyAutoloadSd(it) })
                        AutoRow("Выгружать при выходе", "Освобождать RAM, когда ушёл с экрана",
                            vm.autoUnload, { vm.applyAutoUnload(it) })
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text("Бэкап (папка models)", style = MaterialTheme.typography.labelMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = bkKeys, onCheckedChange = { bkKeys = it })
                            Text("включая ключи + шифр всего файла (пароль мин. 4 симв.)",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        }
                        if (bkKeys) {
                            OutlinedTextField(bkPass, { bkPass = it }, label = { Text("Пароль для ключей") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                visualTransformation = PasswordVisualTransformation())
                            Spacer(Modifier.height(6.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.makeBackup(bkKeys, bkPass) }, modifier = Modifier.weight(1f)) {
                                Text("Бэкап")
                            }
                            OutlinedButton(onClick = { restorePicker.launch("*/*") }, modifier = Modifier.weight(1f)) {
                                Text("Восстановить")
                            }
                        }
                        if (vm.backupMsg.isNotBlank()) {
                            Text(vm.backupMsg, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Автобэкап раз в неделю", style = MaterialTheme.typography.bodyMedium)
                                Text("Без ключей, последние 3 шт.", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary)
                            }
                            Switch(checked = vm.autoBackup, onCheckedChange = { vm.applyAutoBackup(it) })
                        }
                    }
                }
            }
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Генерация (llama.cpp)", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("Макс. токенов: ${vm.maxTokens}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(128, 256, 512).forEach { v ->
                                FilterChip(selected = vm.maxTokens == v, onClick = { vm.applyMaxTokens(v) }, label = { Text("$v") })
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Top-p: ${vm.topP}")
                        Slider(value = vm.topP, onValueChange = { vm.applyTopP((it * 20).toInt() / 20f) },
                            valueRange = 0.5f..1f, modifier = Modifier.fillMaxWidth())
                        Text("Top-k: ${vm.topK}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 20, 40, 80).forEach { v ->
                                FilterChip(selected = vm.topK == v, onClick = { vm.applyTopK(v) },
                                    label = { Text(if (v == 0) "выкл" else "$v") })
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Контекст: ${vm.ctxSize} (при след. загрузке в RAM)")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1024, 2048, 4096, 8192).forEach { v ->
                                FilterChip(selected = vm.ctxSize == v, onClick = { vm.applyCtxSize(v) }, label = { Text("$v") })
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Потоки CPU: ${if (vm.threads == 0) "авто" else "${vm.threads}"}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 4, 6, 8).forEach { v ->
                                FilterChip(selected = vm.threads == v, onClick = { vm.applyThreads(v) },
                                    label = { Text(if (v == 0) "авто" else "$v") })
                            }
                        }
                    }
                }
            }
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Чат и голос", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("Размер текста: ${"%.0f".format(vm.textScale * 100)}%")
                        Slider(value = vm.textScale, onValueChange = { vm.applyTextScale((it * 20).toInt() / 20f) },
                            valueRange = 0.8f..1.4f, modifier = Modifier.fillMaxWidth())
                        Text("Скорость озвучки: ${vm.ttsRate}")
                        Slider(value = vm.ttsRate, onValueChange = { vm.applyTtsRate((it * 20).toInt() / 20f) },
                            valueRange = 0.5f..1.5f, modifier = Modifier.fillMaxWidth())
                        Text("Тон озвучки: ${vm.ttsPitch}")
                        Slider(value = vm.ttsPitch, onValueChange = { vm.applyTtsPitch((it * 20).toInt() / 20f) },
                            valueRange = 0.5f..1.5f, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("О приложении", style = MaterialTheme.typography.titleMedium)
                        Text("NeuroPocket v1.12 • S24 Ultra ready • minSdk 28 • Compose + NDK", style = MaterialTheme.typography.bodySmall)
                        Text("Движки: llama.cpp + whisper + SD native. Всё хранится только на телефоне.", style = MaterialTheme.typography.bodySmall)
                        Text("Встроенного фильтра нет. 18+-контент — ответственность взрослого пользователя.", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = { vm.toggleEngine() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Движок: ${vm.engineLabel()} (переключить)")
                        }
                        Spacer(Modifier.height(6.dp))
                        if (vm.updateInfo != null) {
                            Text(vm.updateInfo!!, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary)
                            if (vm.updateUrl != null) {
                                Spacer(Modifier.height(6.dp))
                                Button(onClick = { vm.downloadUpdate() }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Скачать и установить")
                                }
                                DlRow(vm, "NeuroPocket-update.apk")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.checkUpdates() }, enabled = !vm.updateBusy,
                                modifier = Modifier.weight(1f)) {
                                Text(if (vm.updateBusy) "Проверка…" else "Обновления")
                            }
                            OutlinedButton(onClick = { vm.markOnboarded(false) }, modifier = Modifier.weight(1f)) {
                                Text("Обучение")
                            }
                            OutlinedButton(onClick = {
                                vm.applyAutoFallback(!vm.autoFallback)
                            }, modifier = Modifier.weight(1f)) {
                                Text(if (vm.autoFallback) "Запасной вкл" else "Запасной выкл")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DlRow(vm: AppViewModel, fileName: String) {
    val dl = vm.dlFor(fileName) ?: return
    val id = vm.downloads.entries.find { it.value.fileName == fileName }?.key
    Spacer(Modifier.height(6.dp))
    if (dl.failed) {
        TextButton(onClick = { if (id != null) vm.dismissDownload(id) }) {
            Text(dl.text + " (убрать)", color = MaterialTheme.colorScheme.error)
        }
        return
    }
    if (dl.progress > 0) {
        LinearProgressIndicator(progress = { dl.progress }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(dl.text, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
        if (id != null) {
            TextButton(onClick = { vm.cancelDownload(id) }) { Text("Стоп") }
        }
    }
}

@Composable
private fun AutoRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(sub, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    sub: String,
    query: String,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    if (query.isNotBlank() && !title.contains(query, true) && !sub.contains(query, true)) return
    Column(Modifier.clickable { onClick() }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(10.dp).size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(sub, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary)
        }
        if (showDivider) HorizontalDivider(Modifier.padding(horizontal = 14.dp))
    }
}
