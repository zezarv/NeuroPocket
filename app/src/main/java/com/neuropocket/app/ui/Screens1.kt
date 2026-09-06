package com.neuropocket.app.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.neuropocket.app.AppViewModel
import com.neuropocket.app.voice.VoiceHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: AppViewModel,
    onMenu: () -> Unit = {}
) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var voice = remember { VoiceHelper(ctx) }
    var speakOut by remember { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQ by remember { mutableStateOf("") }
    LaunchedEffect(vm.chatDraft) {
        vm.chatDraft?.let { input = it; vm.clearDraft() }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val arr = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!arr.isNullOrEmpty()) input = arr[0]
        }
    }

    DisposableEffect(Unit) { onDispose { voice.destroy() } }
    val view = LocalView.current
    DisposableEffect(vm.keepScreenOn) {
        view.keepScreenOn = vm.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text(
                    (vm.currentSession()?.title?.take(24) ?: "Чат") + " • " + (vm.activePersona?.name ?: "…"),
                    modifier = Modifier.clickable {
                        renameText = vm.currentSession()?.title ?: ""
                        renameOpen = true
                    }
                )
            },
            navigationIcon = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, contentDescription = "История") } },
            actions = {
                IconButton(onClick = { vm.clearChat() }) { Icon(Icons.Default.Delete, contentDescription = "Очистить") }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Ещё") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Экспорт .md") }, onClick = {
                            menuOpen = false
                            try {
                                val sb = StringBuilder("# " + (vm.currentSession()?.title ?: "Чат") + "\n\n")
                                for (m in vm.messages) {
                                    sb.append(if (m.role == "user") "**Вы:** " else "**ИИ:** ")
                                        .append(m.text).append("\n\n")
                                }
                                val f = java.io.File(ctx.cacheDir, "chat-export.md")
                                f.writeText(sb.toString())
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    ctx, ctx.packageName + ".fileprovider", f)
                                val sh = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/markdown"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                ctx.startActivity(android.content.Intent.createChooser(sh, "Экспорт чата"))
                            } catch (_: Exception) { }
                        })
                        DropdownMenuItem(text = { Text("Заново (другой ответ)") }, onClick = {
                            menuOpen = false
                            vm.regenerate()
                        })
                        DropdownMenuItem(text = { Text(if (searchOpen) "Скрыть поиск" else "Найти в чате") }, onClick = {
                            menuOpen = false
                            searchOpen = !searchOpen
                            if (!searchOpen) searchQ = ""
                        })
                    }
                }
            })
    }) { pad ->
        if (renameOpen) {
            AlertDialog(
                onDismissRequest = { renameOpen = false },
                title = { Text("Переименовать чат") },
                text = {
                    OutlinedTextField(renameText, { renameText = it }, label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                },
                confirmButton = {
                    Button(onClick = {
                        vm.currentSession()?.let { vm.renameSession(it.id, renameText) }
                        renameOpen = false
                    }) { Text("Ок") }
                },
                dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Отмена") } }
            )
        }
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Text(vm.engineLabel() + " • " + vm.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            if (searchOpen) {
                OutlinedTextField(searchQ, { searchQ = it }, label = { Text("Найти…") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(6.dp))
            }
            val shownMsgs = remember(vm.messages, searchQ, searchOpen) {
                if (searchOpen && searchQ.isNotBlank()) vm.messages.filter { it.text.contains(searchQ, true) }
                else vm.messages
            }
            val listState = rememberLazyListState()
            LaunchedEffect(vm.messages.size) { if (vm.messages.isNotEmpty() && !searchOpen) listState.animateScrollToItem(vm.messages.size - 1) }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shownMsgs) { m ->
                    val isUser = m.role == "user"
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(if (isUser) 0.88f else 1f),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = if (isUser)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(if (isUser) "Вы" else (vm.personas.find { it.id == m.personaId }?.name ?: "ИИ"),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(4.dp))
                                if (m.text.isBlank() && vm.busy) {
                                    Text("…", color = MaterialTheme.colorScheme.secondary)
                                } else if (isUser) {
                                    Text(m.text, fontSize = MaterialTheme.typography.bodyLarge.fontSize * vm.textScale)
                                    if (vm.showTime) {
                                        Text(timeAgo(m.ts), style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary)
                                    }
                                } else {
                                    MarkdownText(m.text, fontSize = MaterialTheme.typography.bodyLarge.fontSize * vm.textScale)
                                }
                                if (!isUser && m.text.isNotBlank()) {
                                    Row {
                                        TextButton(onClick = {
                                            scope.launch {
                                                val personaVoice = vm.personas.find { it.id == m.personaId }?.voice ?: ""
                                                if (!vm.speakOut(m.text, personaVoice)) {
                                                    voice.speak(m.text, vm.ttsRate, vm.ttsPitch)
                                                }
                                            }
                                        }) { Text("Озвучить") }
                                        TextButton(onClick = {
                                            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            cm.setPrimaryClip(android.content.ClipData.newPlainText("np", m.text))
                                        }) { Text("Копия") }
                                    }
                                }
                                if (vm.showTime && m.text.isNotBlank()) {
                                    Text(
                                        timeAgo(m.ts), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
                if (vm.busy) { item { LinearProgressIndicator(Modifier.fillMaxWidth()) } }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("Сообщение…") }, maxLines = 4)
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = {
                    try {
                        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                        }
                        speechLauncher.launch(i)
                    } catch (_: Exception) { voice.listenOnce({ input = it }) }
                }) { Icon(Icons.Default.Mic, contentDescription = "Голос") }
                Spacer(Modifier.width(2.dp))
                if (vm.busy) {
                    Button(onClick = { vm.stopGen() }) { Icon(Icons.Default.Stop, contentDescription = "Стоп") }
                } else {
                    Button(onClick = {
                        val t = input.trim(); if (t.isEmpty()) return@Button
                        input = ""
                        vm.send(t) { reply -> if (speakOut && t.startsWith("!say ")) scope.launch { voice.speak(reply, vm.ttsRate, vm.ttsPitch) } }
                    }) { Icon(Icons.Default.Send, contentDescription = "Отправить") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = speakOut, onCheckedChange = { speakOut = it })
                Text("Озвучка по !say", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(vm.personas.size.toString() + " персон", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonasScreen(
    vm: AppViewModel,
    onOpenPersona: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenRound: () -> Unit = {}
) {
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("\uD83D\uDE00") }
    var desc by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var diceNsfw by remember { mutableStateOf(false) }
    var temp by remember { mutableStateOf(0.7f) }
    var pvoice by remember { mutableStateOf("") }
    var voiceMenu by remember { mutableStateOf(false) }
    val ctxP = LocalContext.current
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        if (uri != null) vm.importPersonaFile(uri)
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Персоны") },
            actions = {
                TextButton(onClick = { importPicker.launch("application/json") }) { Text("Импорт") }
            })
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedButton(onClick = onOpenRound, modifier = Modifier.fillMaxWidth()) {
                    Text("Круглый стол (групповой чат)")
                }
            }
            item {
                OutlinedTextField(search, { search = it }, label = { Text("Поиск персон…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            val dialogs = vm.personas.filter { vm.personaMessages(it.id).isNotEmpty() }
            if (dialogs.isNotEmpty() && search.isBlank()) {
                item {
                    Text("Диалоги", style = MaterialTheme.typography.titleMedium)
                }
                items(dialogs, key = { it.id }) { p ->
                    val last = vm.personaMessages(p.id).lastOrNull()
                    ElevatedCard(onClick = { onOpenChat(p.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AvatarView(p.avatarPath, p.avatarEmoji, 48.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(p.name, style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f))
                                    if (last != null) Text(timeAgo(last.ts),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary)
                                }
                                Text(
                                    if (last == null) "Нет сообщений"
                                    else (if (last.role == "user") "Вы: " else "") + last.text.take(80),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary, maxLines = 1
                                )
                            }
                        }
                    }
                }
                item {
                    Text("Все персоны", style = MaterialTheme.typography.titleMedium)
                }
            }
            val shown = vm.personas.filter {
                search.isBlank() || it.name.contains(search, true) || it.desc.contains(search, true) ||
                    it.tags.any { t -> t.contains(search, true) }
            }
            items(shown) { p ->
                val sel = vm.activePersona?.id == p.id
                ElevatedCard(
                    onClick = { onOpenPersona(p.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(Modifier.padding(14.dp)) {
                        AvatarView(p.avatarPath, p.avatarEmoji, 56.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.name + if (sel) " • активна" else "",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f))
                                IconButton(onClick = { onOpenChat(p.id) }) {
                                    Icon(Icons.Default.Chat, contentDescription = "Открыть чат",
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (p.desc.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(p.desc, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary, maxLines = 2)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (p.nsfwAllowed) AssistChip(onClick = {},
                                    label = { Text("18+") },
                                    colors = AssistChipDefaults.assistChipColors(
                                        labelColor = MaterialTheme.colorScheme.error))
                                p.tags.take(3).forEach { t ->
                                    AssistChip(onClick = { search = t }, label = { Text(t) })
                                }
                                Text("t=${p.temperature}", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.align(Alignment.CenterVertically))
                                if (p.voice.isNotBlank()) {
                                    Text("🔊${p.voice.take(14)}", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.align(Alignment.CenterVertically))
                                }
                            }
                        }
                    }
                }
            }
            item {
                HorizontalDivider()
                Text("Новая персона", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(name, { name = it }, label = { Text("Имя") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(desc, { desc = it }, label = { Text("Короткое описание для карточки") },
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(prompt, { prompt = it }, label = { Text("Системный промпт (характер, стиль, 18+ allowed)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(emoji, { emoji = it }, label = { Text("Эмодзи") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(tags, { tags = it }, label = { Text("Теги через запятую") }, modifier = Modifier.weight(2f))
                }
                Text("Температура: ${"%.1f".format(temp)} (0.0 точно • 1.5 дико)")
                Slider(value = temp, onValueChange = { temp = it }, valueRange = 0f..1.5f, steps = 14,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val d = com.neuropocket.app.data.RandomFactory.roll(diceNsfw)
                        name = d.name; prompt = d.prompt; emoji = d.emoji
                        desc = d.desc; tags = d.tags.joinToString(", ")
                    }, modifier = Modifier.weight(1f)) { Text("Случайная") }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Checkbox(checked = diceNsfw, onCheckedChange = { diceNsfw = it })
                        Text("18+", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Голос: ${pvoice.ifBlank { "общий" }}", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium)
                    Box {
                        TextButton(onClick = { voiceMenu = true }) { Text("Выбрать") }
                        DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
                            DropdownMenuItem(text = { Text("общий") }, onClick = { pvoice = ""; voiceMenu = false })
                            vm.voiceDirs.forEach { v ->
                                DropdownMenuItem(text = { Text(v) }, onClick = { pvoice = v; voiceMenu = false })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Button(onClick = { vm.addPersona(name, prompt, emoji, desc, tags, temp, pvoice); name = ""; prompt = ""; desc = ""; tags = ""; pvoice = "" }, modifier = Modifier.fillMaxWidth()) { Text("Создать") }
                Text("Импорт подхватит и аватар: положи рядом persona-<имя>.jpg.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Text("Совет: для NSFW-персон опиши возраст 18+, границы и стиль напрямую в промпте — фильтра в приложении нет, всё локально.",
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    vm: AppViewModel,
    onBack: (() -> Unit)? = null,
    initialCard: String = "",
    onOpenTool: (String) -> Unit = {},
    onOpenChats: () -> Unit = {},
    onOpenUtils: () -> Unit = {}
) {
    var box by remember { mutableStateOf("") }
    var agentTask by remember { mutableStateOf("") }
    // Phase B: простая навигация — поиск, категории, недавние.
    var toolSearch by remember { mutableStateOf("") }
    var toolCat by remember { mutableStateOf("Все") }
    val toolScope = rememberCoroutineScope()
    var photoPrompt by remember { mutableStateOf("") }
    var photoNeg by remember { mutableStateOf("blurry, low quality, watermark") }
    var photoSteps by remember { mutableStateOf("6") }
    var photoSize by remember { mutableIntStateOf(512) }
    var photoSampler by remember { mutableStateOf("lcm") }
    var photoSeed by remember { mutableStateOf("") }
    var photoHires by remember { mutableStateOf(false) }
    var photoStrength by remember { mutableStateOf(0.6f) }
    var photoInit by remember { mutableStateOf<java.io.File?>(null) }
    var visPrompt by remember { mutableStateOf("") }
    var visFile by remember { mutableStateOf<java.io.File?>(null) }
    LaunchedEffect(vm.pendingVision) {
        vm.consumeVision()?.let { visFile = it }
    }
    var noteFind by remember { mutableStateOf("") }
    var noteName by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var ragQ by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var recTick by remember { mutableIntStateOf(0) }
    // P0.6: подтверждение удаления крупных файлов.
    var confirmDelete by remember { mutableStateOf<java.io.File?>(null) }
    val ctx = LocalContext.current
    var rec by remember { mutableStateOf<com.neuropocket.app.voice.AudioRec?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val f = java.io.File(ctx.getExternalFilesDir(null), "models/mic-${System.currentTimeMillis()}.wav")
            val r = com.neuropocket.app.voice.AudioRec(f)
            if (r.start()) { rec = r; recording = true }
        }
    }
    val wavPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (ctx.copyUriToModels(uri, "audio") != null) vm.scanModels()
    }
    val photoInitPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val name = "init-${System.currentTimeMillis()}.jpg"
            val out = java.io.File(ctx.getExternalFilesDir(null), "pictures/$name")
            out.parentFile?.mkdirs()
            ctx.contentResolver.openInputStream(uri)?.use { ins -> out.outputStream().use { ins.copyTo(it) } }
            photoInit = out
            vm.refreshGallery()
        } catch (_: Exception) { }
    }
    val imgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val name = "ask-${System.currentTimeMillis()}.jpg"
            val out = java.io.File(ctx.getExternalFilesDir(null), "pictures/$name")
            out.parentFile?.mkdirs()
            ctx.contentResolver.openInputStream(uri)?.use { ins -> out.outputStream().use { ins.copyTo(it) } }
            visFile = out
            vm.refreshGallery()
        } catch (_: Exception) { }
    }
    var camUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            camUri?.let { u ->
                try {
                    val name = "cam-${System.currentTimeMillis()}.jpg"
                    val out = java.io.File(ctx.getExternalFilesDir(null), "pictures/$name")
                    out.parentFile?.mkdirs()
                    ctx.contentResolver.openInputStream(u)?.use { ins -> out.outputStream().use { ins.copyTo(it) } }
                    visFile = out
                    vm.refreshGallery()
                } catch (_: Exception) { }
            }
        }
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            try {
                val tmp = java.io.File(ctx.cacheDir, "cam-tmp.jpg")
                val u = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", tmp)
                camUri = u
                camLauncher.launch(u)
            } catch (_: Exception) { }
        }
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Песочница / Инструменты") },
            navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                }
            })
    }) { pad ->
        val listState = rememberLazyListState()
        LaunchedEffect(initialCard) {
            val idx = when (initialCard) {
                "voice" -> 1
                "vision" -> 2
                "rag" -> 3
                "transcriber" -> 4
                "agent" -> 5
                "photo" -> 6
                "sandbox" -> 7
                else -> -1
            }
            if (idx >= 0) listState.scrollToItem(idx)
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(pad).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                ToolsNavCard(
                    search = toolSearch, onSearch = { toolSearch = it },
                    cat = toolCat, onCat = { toolCat = it },
                    recent = vm.recentTools(4),
                    onOpenTool = onOpenTool,
                    onJump = { idx -> toolScope.launch { try { listState.scrollToItem(idx) } catch (_: Exception) { } } }
                )
            }
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Голосовой чат (hands-free)", style = MaterialTheme.typography.titleSmall)
                        Text(vm.ttsInfo + " • whisper " + if (vm.whisperLoaded) "в RAM" else "нет",
                            style = MaterialTheme.typography.bodySmall)
                        if (vm.hfStatus.isNotBlank()) {
                            Text(vm.hfStatus, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        vm.hfLog.takeLast(4).forEach { ln ->
                            Text(ln, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary, maxLines = 2)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Пауза: ${(vm.vadSil * 32) / 1000.0}с", style = MaterialTheme.typography.labelSmall)
                                Slider(value = vm.vadSil.toFloat(), onValueChange = { vm.applyVadSil(it.toInt()) },
                                    valueRange = 15f..90f, modifier = Modifier.fillMaxWidth())
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Мин. речь: ${(vm.vadMin / 16000.0)}с", style = MaterialTheme.typography.labelSmall)
                                Slider(value = vm.vadMin.toFloat(), onValueChange = { vm.applyVadMin(it.toInt()) },
                                    valueRange = 4000f..24000f, steps = 4, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("ru", "en", "auto").forEach { v ->
                                FilterChip(selected = vm.sttLang == v, onClick = { vm.applySttLang(v) },
                                    label = { Text(v) })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Перебивать голосом (barge-in)", style = MaterialTheme.typography.bodyMedium)
                                Text("Нужен VAD. Эхо колонок может срабатывать ложно.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary)
                            }
                            Switch(checked = vm.bargeIn, onCheckedChange = { vm.applyBargeIn(it) })
                        }
                        if (!vm.hfRunning) {
                            Button(onClick = {
                                val ok = androidx.core.content.ContextCompat.checkSelfPermission(
                                    ctx, android.Manifest.permission.RECORD_AUDIO) ==
                                    android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (ok) vm.startHandsFree()
                                else permLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }, modifier = Modifier.fillMaxWidth()) { Text("Начать разговор") }
                            Text("Слушает через VAD, отвечает голосом персоны. Всё локально.",
                                style = MaterialTheme.typography.labelSmall)
                        } else {
                            Button(onClick = { vm.stopHandsFree() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Стоп")
                            }
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Фото-вопрос (зрение)", style = MaterialTheme.typography.titleSmall)
                        Text(vm.visionInfo, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { imgPicker.launch("image/*") }, modifier = Modifier.weight(1f)) {
                                Text("Из галереи")
                            }
                            OutlinedButton(onClick = {
                                val ok = androidx.core.content.ContextCompat.checkSelfPermission(
                                    ctx, android.Manifest.permission.CAMERA) ==
                                    android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (ok) {
                                    try {
                                        val tmp = java.io.File(ctx.cacheDir, "cam-tmp.jpg")
                                        val u = androidx.core.content.FileProvider.getUriForFile(
                                            ctx, ctx.packageName + ".fileprovider", tmp)
                                        camUri = u
                                        camLauncher.launch(u)
                                    } catch (_: Exception) { }
                                } else camPerm.launch(android.Manifest.permission.CAMERA)
                            }, modifier = Modifier.weight(1f)) { Text("Снять") }
                        }
                        if (visFile != null) {
                            Text("Файл: ${visFile!!.name} (${visFile!!.length() / 1024} КБ)",
                                style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedTextField(visPrompt, { visPrompt = it },
                            label = { Text("Вопрос про фото…") },
                            modifier = Modifier.fillMaxWidth(), minLines = 2)
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = { visFile?.let { vm.describePhoto(it, visPrompt) } },
                            enabled = !vm.visionBusy && visFile != null,
                            modifier = Modifier.fillMaxWidth()) {
                            Text(if (vm.visionBusy) "Смотрю…" else "Спросить")
                        }
                        if (vm.visionBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (vm.visionResult.isNotBlank() && !vm.visionResult.startsWith("__ERR")) {
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(vm.visionResult)
                                    TextButton(onClick = {
                                        vm.discussInChat("Про фото: ${vm.visionResult.take(1000)}")
                                        onOpenChats()
                                    }) { Text("В новый чат") }
                                }
                            }
                        }
                    }
                }
            }
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Заметки и RAG (память)", style = MaterialTheme.typography.titleSmall)
                        Text(vm.embedInfo + " • чанков: ${vm.ragIndexed}",
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(noteFind, { noteFind = it }, label = { Text("Найти заметку…") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                        val shownNotes = remember(vm.noteFiles, noteFind) {
                            if (noteFind.isBlank()) vm.noteFiles
                            else vm.noteFiles.filter { it.contains(noteFind, true) }
                        }
                        if (shownNotes.isEmpty()) {
                            Text("Ничего нет — создай ниже (.md).", style = MaterialTheme.typography.bodySmall)
                        }
                        shownNotes.take(20).forEach { n ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("• $n", modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = {
                                    noteName = n.removeSuffix(".md"); noteText = vm.readNote(n)
                                }) { Text("Открыть") }
                                TextButton(onClick = { vm.deleteNote(n) }) { Text("Удалить") }
                            }
                        }
                        OutlinedTextField(noteName, { noteName = it }, label = { Text("Имя заметки") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(noteText, { noteText = it }, label = { Text("Текст…") },
                            modifier = Modifier.fillMaxWidth(), minLines = 3)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                vm.saveNote(noteName, noteText); noteName = ""; noteText = ""
                            }, modifier = Modifier.weight(1f)) { Text("Сохранить") }
                            OutlinedButton(onClick = { vm.reindexNotes() },
                                enabled = !vm.ragBusy, modifier = Modifier.weight(1f)) {
                                Text("Индексировать")
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(ragQ, { ragQ = it }, label = { Text("Вопрос по заметкам…") },
                            modifier = Modifier.fillMaxWidth(), minLines = 2)
                        Button(onClick = { vm.askNotes(ragQ) }, enabled = !vm.ragBusy,
                            modifier = Modifier.fillMaxWidth()) {
                            Text(if (vm.ragBusy) "Ищу…" else "Спросить")
                        }
                        if (vm.ragBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (vm.ragResult.isNotBlank()) {
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(vm.ragResult)
                                    TextButton(onClick = {
                                        vm.discussInChat("По заметкам: ${vm.ragResult.take(1000)}")
                                        onOpenChats()
                                    }) { Text("В новый чат") }
                                }
                            }
                        }
                    }
                }
            }
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Транскрибатор (whisper локально)", style = MaterialTheme.typography.titleSmall)
                        Text(vm.whisperInfo, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { wavPicker.launch("audio/*") }, modifier = Modifier.weight(1f)) { Text("Выбрать аудио") }
                            if (!recording) {
                                OutlinedButton(onClick = {
                                    val ok = androidx.core.content.ContextCompat.checkSelfPermission(
                                        ctx, android.Manifest.permission.RECORD_AUDIO) ==
                                        android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (ok) {
                                        val f = java.io.File(ctx.getExternalFilesDir(null), "models/mic-${System.currentTimeMillis()}.wav")
                                        val r = com.neuropocket.app.voice.AudioRec(f)
                                        if (r.start()) { rec = r; recording = true }
                                    } else permLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }, modifier = Modifier.weight(1f)) { Text("● Запись") }
                            } else {
                                OutlinedButton(onClick = {
                                    rec?.stop(); rec = null; recording = false
                                    vm.refreshWhisperState(); vm.scanModels()
                                }, modifier = Modifier.weight(1f)) { Text("■ Стоп") }
                            }
                        }
                        LaunchedEffect(recording) {
                            while (recording) {
                                kotlinx.coroutines.delay(500)
                                recTick++
                            }
                        }
                        if (recording) Text("Запись… ${recTick / 2} c (WAV 16 кГц, макс 10 мин)", color = MaterialTheme.colorScheme.error)
                        Text("Модели .bin: ${vm.whisperFiles.size}", style = MaterialTheme.typography.labelSmall)
                        vm.whisperFiles.forEach { f ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("• ${f.name} (${f.length() / 1048576} МБ)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { vm.loadWhisperToRam(f) }) { Text("В RAM") }
                                TextButton(onClick = { confirmDelete = f }) { Text("Удалить") }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("ru" to "Русский", "en" to "English", "auto" to "Авто").forEach { (v, nm) ->
                                FilterChip(selected = vm.sttLang == v, onClick = { vm.applySttLang(v) }, label = { Text(nm) })
                            }
                        }
                        Text("Аудио (WAV 16 кГц моно, до 10 мин):", style = MaterialTheme.typography.labelMedium)
                        if (vm.wavFiles.isEmpty()) Text("Нет аудиофайлов — нажми «Выбрать аудио».", style = MaterialTheme.typography.bodySmall)
                        vm.wavFiles.forEach { f ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("• ${f.name} (${f.length() / 1048576} МБ)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { vm.transcribeWav(f, vm.sttLang) }) { Text("Текст") }
                            }
                        }
                        if (vm.whisperResult.isNotBlank() && !vm.whisperResult.startsWith("__ERR")) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Таймкоды", modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelMedium)
                                Switch(checked = vm.showTimed, onCheckedChange = { vm.toggleTimed() })
                            }
                        }
                        if (vm.showTimed) {
                            vm.whisperTimed.take(60).forEach { ln ->
                                Text(
                                    text = fmtTime(ln.from) + " → " + fmtTime(ln.to) + "  " + ln.text,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        if (vm.whisperResult.isNotBlank()) {
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(vm.whisperResult)
                                    TextButton(onClick = {
                                        vm.discussInChat("Обработай транскрипт: ${vm.whisperResult.take(1000)}")
                                        onOpenChats()
                                    }) { Text("В новый чат") }
                                }
                            }
                        }
                    }
                }
                Text("Микрофон в реальном времени — через 🎤 в чате (системный STT). Файловое распознавание — карточка выше (whisper, офлайн).", style = MaterialTheme.typography.labelSmall)
            }
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Агент (реальные действия, локально)", style = MaterialTheme.typography.titleSmall)
                        Text("Движок: ${vm.engineLabel()}", style = MaterialTheme.typography.bodySmall)
                        Text("Действия: заметки, саммари, перевод, анализ, черновик поста. Недоступное честно помечается.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(agentTask, { agentTask = it }, label = { Text("Задача агенту…") },
                            modifier = Modifier.fillMaxWidth(), minLines = 2)
                        Spacer(Modifier.height(6.dp))
                        if (!vm.agentRunning) {
                            Button(onClick = { vm.runAgent(agentTask) }, enabled = !vm.busy,
                                modifier = Modifier.fillMaxWidth()) {
                                Text("Запустить агента")
                            }
                        } else {
                            Button(onClick = { vm.stopAgent() },
                                modifier = Modifier.fillMaxWidth()) {
                                Text("Стоп")
                            }
                        }
                        if (vm.agentRunning) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (vm.agentPlanRaw.isNotBlank()) {
                            Text("План: ${vm.agentPlanRaw.take(400)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.height(4.dp))
                        }
                        vm.agentSteps.forEachIndexed { i, s ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    when (s.status) { "run" -> "⏳"; "done" -> "✅"; "fail" -> "❌"; else -> "•" },
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text("Шаг ${i + 1}: ${s.text}", style = MaterialTheme.typography.bodyMedium)
                                    if (s.result.isNotBlank()) Text(s.result.take(400),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        if (vm.agentResult.isNotBlank()) {
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("Итог:", style = MaterialTheme.typography.labelMedium)
                                    Text(vm.agentResult)
                                    TextButton(onClick = {
                                        vm.discussInChat("Агент выполнил задачу «${agentTask.take(200)}». Итог: ${vm.agentResult.take(1000)}")
                                        onOpenChats()
                                    }) {
                                        Text("В новый чат")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Фото (SD локально, 512px)", style = MaterialTheme.typography.titleSmall)
                        Text(vm.sdInfo + " • CPU: минуты на кадр, не сворачивай", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Text("Движок SD: " + com.neuropocket.app.core.SdEngine.statusRu(vm.sdEngineState),
                            style = MaterialTheme.typography.labelSmall)
                        if (vm.sdEngineState == com.neuropocket.app.core.SdEngineState.MISSING ||
                            vm.sdEngineState == com.neuropocket.app.core.SdEngineState.ERROR
                        ) {
                            if (vm.sdEngineState == com.neuropocket.app.core.SdEngineState.ERROR) {
                                Text(
                                    vm.sdEngineError ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (vm.sdEngineState == com.neuropocket.app.core.SdEngineState.ERROR) {
                                    Button(onClick = { vm.retrySdEngine() },
                                        modifier = Modifier.weight(1f)) { Text("Повторить") }
                                    OutlinedButton(onClick = { vm.deleteSdEngine() },
                                        modifier = Modifier.weight(1f)) { Text("Удалить движок") }
                                } else if (vm.sdEngineUrl == null) {
                                    OutlinedButton(onClick = { vm.resolveSdEngineUrl() },
                                        enabled = !vm.sdEngineBusy, modifier = Modifier.weight(1f)) {
                                        Text(if (vm.sdEngineBusy) "Ищу…" else "Найти движок")
                                    }
                                } else {
                                    Button(onClick = {
                                        vm.sdEngineUrl?.let { vm.downloadSdEngine(it) }
                                    }, modifier = Modifier.weight(1f)) { Text("Скачать (51 МБ)") }
                                }
                            }
                            DlRow(vm, "libnpsd.so")
                            Spacer(Modifier.height(4.dp))
                        }
                        if (vm.sdEngineState == com.neuropocket.app.core.SdEngineState.DOWNLOADING ||
                            vm.sdEngineState == com.neuropocket.app.core.SdEngineState.VERIFYING ||
                            vm.sdEngineState == com.neuropocket.app.core.SdEngineState.INSTALLING
                        ) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            DlRow(vm, "libnpsd.so")
                            Spacer(Modifier.height(4.dp))
                        }
                        Text("Модели .safetensors: ${vm.sdFiles.size}", style = MaterialTheme.typography.labelSmall)
                        vm.sdFiles.forEach { f ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("• ${f.name} (${f.length() / 1048576} МБ)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { vm.loadSdToRam(f) }) { Text("В RAM") }
                                TextButton(onClick = { confirmDelete = f }) { Text("Удалить") }
                            }
                        }
                        OutlinedTextField(photoPrompt, { photoPrompt = it }, label = { Text("Промпт (лучше на английском)…") },
                            modifier = Modifier.fillMaxWidth(), minLines = 2)
                        OutlinedTextField(photoNeg, { photoNeg = it }, label = { Text("Негативный промпт") },
                            modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "Фото" to "blurry, low quality, watermark, text",
                                "Аниме" to "lowres, bad anatomy, bad hands, watermark",
                                "Чисто" to ""
                            ).forEach { (nm, v) ->
                                FilterChip(selected = photoNeg == v,
                                    onClick = { photoNeg = v }, label = { Text(nm) })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = photoHires, onCheckedChange = { photoHires = it })
                            Text("HiRes-фикс x1.5 (намного дольше)",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(photoSteps, { photoSteps = it.filter { c -> c.isDigit() }.take(2) },
                                label = { Text("Шаги (2-30)") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(photoSeed, { photoSeed = it.filter { c -> c.isDigit() }.take(12) },
                                label = { Text("Seed (пусто=случ.)") }, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Размер (768 медленно):", style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(384, 512, 768).forEach { s ->
                                FilterChip(selected = photoSize == s, onClick = { photoSize = s }, label = { Text("$s") })
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Сэмплер:", style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("lcm" to "LCM", "euler_a" to "Euler a", "dpm++2m" to "DPM++").forEach { (v, nm) ->
                                FilterChip(selected = photoSampler == v, onClick = { photoSampler = v }, label = { Text(nm) })
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Исходник (img2img, необязательно):", style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { photoInitPicker.launch("image/*") }, modifier = Modifier.weight(1f)) {
                                Text(if (photoInit == null) "Выбрать" else "Другое")
                            }
                            if (photoInit != null) {
                                OutlinedButton(onClick = { photoInit = null }, modifier = Modifier.weight(1f)) {
                                    Text("Убрать")
                                }
                            }
                        }
                        if (photoInit != null) {
                            Text(
                                "Файл: " + (photoInit?.name ?: "") + ", сила переделки: " +
                                    "%.1f".format(photoStrength)
                            )
                            Slider(value = photoStrength, onValueChange = { photoStrength = it },
                                valueRange = 0.2f..1f, modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(6.dp))
                        if (!vm.sdBusy) {
                            Button(onClick = {
                                vm.renderPhoto(
                                    photoPrompt, photoNeg, photoSize,
                                    photoSteps.toIntOrNull() ?: 6, 1.0f, photoSampler,
                                    photoSeed.toLongOrNull() ?: 0L, photoInit, photoStrength,
                                    photoHires
                                )
                            }, enabled = vm.sdLoaded, modifier = Modifier.fillMaxWidth()) {
                                Text(if (vm.sdLoaded) "Сгенерировать" else "Сначала модель в RAM")
                            }
                        } else {
                            Button(onClick = { vm.cancelSd() }, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Галерея (${vm.gallery.size}):", style = MaterialTheme.typography.labelMedium)
                        if (vm.gallery.isEmpty()) Text("Пока пусто.", style = MaterialTheme.typography.bodySmall)
                        vm.gallery.forEach { f ->
                            AvatarView(
                                path = f.absolutePath, emoji = "", size = 320.dp,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(f.name, style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.weight(1f))
                                    TextButton(onClick = {
                                        try {
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                ctx, ctx.packageName + ".fileprovider", f)
                                            val sh = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                type = "image/png"
                                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            ctx.startActivity(android.content.Intent.createChooser(sh, "Поделиться"))
                                        } catch (_: Exception) { }
                                    }) { Text("Отправить") }
                                    TextButton(onClick = {
                                        try { f.delete() } catch (_: Exception) { }
                                        vm.refreshGallery()
                                    }) { Text("Удалить") }
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                            TextButton(onClick = { vm.refreshGallery(); vm.scanModels() }) { Text("Обновить") }
                        }
                    }
                }
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Текстовые среды", style = MaterialTheme.typography.titleSmall)
                        Text("У каждой — своя история, отдельно от чата.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(8.dp))
                        val q = toolSearch.trim().lowercase()
                        com.neuropocket.app.data.ToolCatalog.textTools
                            .filter { t ->
                                val catOk = toolCat == "Все" ||
                                    (if (t.id == "vibecode") "Разработчик" else "AI Текст") == toolCat
                                val qOk = q.isBlank() ||
                                    t.title.lowercase().contains(q) || t.hint.lowercase().contains(q)
                                catOk && qOk
                            }
                            .forEach { t ->
                                val n = vm.toolHistory(t.id).size
                                OutlinedButton(
                                    onClick = { onOpenTool(t.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("${t.title}" + if (n > 0) " ($n)" else "")
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                        if (toolCat == "Все" || toolCat == "Утилиты") {
                            if (q.isBlank() || "утилит".contains(q) || "калькулятор".contains(q) || "json".contains(q)) {
                                OutlinedButton(onClick = onOpenUtils, modifier = Modifier.fillMaxWidth()) { Text("Утилиты: калькулятор, JSON, Base64…") }
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = { agentTask = box }, modifier = Modifier.fillMaxWidth()) { Text("Отправить задачу агенту ↑") }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(box, { box = it }, label = { Text("Быстрая задача агенту…") },
                            modifier = Modifier.fillMaxWidth(), minLines = 2)
                    }
                }
            }
        }
    }
    confirmDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Удалить файл?") },
            text = {
                Text(
                    "Имя: ${f.name}\n" +
                        "Размер: ${vm.displaySizeMb(f)} МБ\n" +
                        "Тип: ${com.neuropocket.app.core.ModelRoles.labelRu(vm.roleForFile(f))}"
                )
            },
            confirmButton = {
                Button(onClick = { vm.deleteModelFile(f); confirmDelete = null }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Отмена") } }
        )
    }
}

/** Phase B: простая навигация по инструментам — поиск, категории, недавние, разделы. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolsNavCard(
    search: String,
    onSearch: (String) -> Unit,
    cat: String,
    onCat: (String) -> Unit,
    recent: List<Pair<String, String>>,
    onOpenTool: (String) -> Unit,
    onJump: (Int) -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Быстрый доступ", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(search, onSearch, label = { Text("Найти инструмент…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Все", "AI Текст", "Разработчик", "Утилиты").forEach { c ->
                    FilterChip(selected = cat == c, onClick = { onCat(c) }, label = { Text(c) })
                }
            }
            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Недавние:", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    recent.forEach { (id, title) ->
                        AssistChip(onClick = { onOpenTool(id) }, label = { Text(title) })
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Разделы:", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Голос" to 1, "Зрение" to 2, "Память" to 3,
                    "Аудио" to 4, "Агент" to 5, "Фото" to 6, "Текст" to 7
                ).forEach { (t, idx) ->
                    AssistChip(onClick = { onJump(idx) }, label = { Text(t) })
                }
            }
        }
    }
}
