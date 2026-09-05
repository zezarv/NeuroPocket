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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuropocket.app.AppViewModel
import com.neuropocket.app.data.Persona
import com.neuropocket.app.voice.VoiceHelper
import kotlinx.coroutines.launch

/** Личное меню персоны: фото, инфо, действия. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaDetailScreen(vm: AppViewModel, personaId: String, onBack: () -> Unit, onOpenChat: (String) -> Unit, onEdit: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val p = vm.personas.find { it.id == personaId }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val tmp = java.io.File(ctx.cacheDir, "av-tmp.jpg")
            ctx.contentResolver.openInputStream(uri)?.use { ins -> tmp.outputStream().use { ins.copyTo(it) } }
            vm.setAvatarFromFile(personaId, tmp)
        } catch (_: Exception) { }
    }
    if (p == null) {
        Scaffold(topBar = { TopAppBar(title = { Text("Персона") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") }
        }) }) { pad ->
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Персона удалена.")
            }
        }
        return
    }
    val msgs = vm.personaMessages(p.id)
    val posts = remember(vm.posts) { vm.posts.count { it.authorId == p.id } }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(p.name) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") } },
            actions = { IconButton(onClick = { vm.clearAvatar(p.id) }) { Icon(Icons.Default.Refresh, contentDescription = "Сбросить фото") } }
        )
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarView(p.avatarPath, p.avatarEmoji, 84.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        if (p.desc.isNotBlank()) Text(p.desc, color = MaterialTheme.colorScheme.secondary)
                        Text("Сообщений: ${msgs.size} • Постов: $posts • t=${p.temperature}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (p.nsfwAllowed) AssistChip(onClick = {}, label = { Text("18+") })
                    p.tags.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                    if (p.voice.isNotBlank()) AssistChip(onClick = {}, label = { Text("🔊${p.voice.take(12)}") })
                }
            }
            item {
                Button(onClick = { onOpenChat(p.id) }, modifier = Modifier.fillMaxWidth()) { Text("Открыть чат") }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onEdit(p.id) }, modifier = Modifier.weight(1f)) {
                        Text("Изменить")
                    }
                    OutlinedButton(onClick = { vm.selectPersona(p.id) }, modifier = Modifier.weight(1f)) {
                        Text(if (vm.activePersona?.id == p.id) "Активна ✓" else "В общие чаты")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickAvatar.launch("image/*") }, modifier = Modifier.weight(1f)) {
                        Text("Фото")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.genAvatar(p.id) }, enabled = !vm.sdBusy,
                        modifier = Modifier.weight(1f)) {
                        Text(if (vm.sdBusy) "Рисую…" else "Сгенерировать (SD)")
                    }
                    OutlinedButton(onClick = { vm.aiPost(p.id, 2) }, modifier = Modifier.weight(1f)) {
                        Text("ИИ-пост")
                    }
                }
                if (vm.sdBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            item {
                Text("Характер", style = MaterialTheme.typography.titleSmall)
                Text(p.systemPrompt, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary)
            }
            item {
                val scope = rememberCoroutineScope()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val f = vm.exportPersonaFile(p.id)
                            if (f != null) {
                                try {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        ctx, ctx.packageName + ".fileprovider", f)
                                    val sh = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    ctx.startActivity(android.content.Intent.createChooser(sh, "Персона"))
                                } catch (_: Exception) { }
                            }
                        }
                    }, modifier = Modifier.weight(1f)) { Text("Поделиться") }
                    TextButton(onClick = { vm.deletePersona(p.id); onBack() },
                        modifier = Modifier.weight(1f)) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/** Отдельная среда чата с персоной (не в общем списке). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaChatScreen(vm: AppViewModel, personaId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = vm.personas.find { it.id == personaId }
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val voice = remember { VoiceHelper(ctx) }
    DisposableEffect(Unit) { onDispose { voice.destroy() } }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val arr = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!arr.isNullOrEmpty()) input = arr[0]
        }
    }

    if (p == null) {
        Scaffold(topBar = { TopAppBar(title = { Text("Чат") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") }
        }) }) { pad ->
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { Text("Персона удалена.") }
        }
        return
    }
    val msgs = vm.personaMessages(p.id)
    Scaffold(topBar = {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarView(p.avatarPath, p.avatarEmoji, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(p.name)
                }
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") } },
            actions = {
                IconButton(onClick = { vm.regeneratePersona(p) }) { Icon(Icons.Default.Refresh, contentDescription = "Заново") }
                IconButton(onClick = { vm.clearPersonaChat(p.id) }) { Icon(Icons.Default.Delete, contentDescription = "Очистить") }
            }
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            val listState = rememberLazyListState()
            LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(msgs) { m ->
                    val isUser = m.role == "user"
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                        if (!isUser) {
                            AvatarView(p.avatarPath, p.avatarEmoji, 32.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 0.92f),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = if (isUser)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(Modifier.padding(11.dp)) {
                                if (m.text.isBlank() && vm.pBusy) {
                                    Text("…", color = MaterialTheme.colorScheme.secondary)
                                } else if (isUser) {
                                    Text(m.text, fontSize = MaterialTheme.typography.bodyLarge.fontSize * vm.textScale)
                                    if (vm.showTime) {
                                        Text(timeAgo(m.ts), style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary)
                                    }
                                } else {
                                    MarkdownText(m.text, fontSize = MaterialTheme.typography.bodyLarge.fontSize * vm.textScale)
                                    if (vm.showTime && m.text.isNotBlank()) {
                                        Text(timeAgo(m.ts), style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                                if (!isUser && m.text.isNotBlank()) {
                                    Row {
                                        TextButton(onClick = {
                                            scope.launch {
                                                if (!vm.speakOut(m.text, p.voice)) {
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
                            }
                        }
                    }
                }
                if (vm.pBusy) { item { LinearProgressIndicator(Modifier.fillMaxWidth()) } }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("Сообщение для ${p.name}…") }, maxLines = 4)
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
                if (vm.pBusy) {
                    Button(onClick = { vm.stopPersona() }) { Icon(Icons.Default.Stop, contentDescription = "Стоп") }
                } else {
                    var lastClick by remember { mutableStateOf(0L) }
                    Button(onClick = {
                        val now = System.currentTimeMillis()
                        if (now - lastClick < 800) return@Button
                        lastClick = now
                        val t = input.trim(); if (t.isEmpty()) return@Button
                        input = ""
                        vm.sendPersona(p, t)
                    }) { Icon(Icons.Default.Send, contentDescription = "Отправить") }
                }
            }
        }
    }
}

/** Редактирование персоны: всё то же, что при создании, плюс движок и 18+. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaEditScreen(vm: AppViewModel, personaId: String, onBack: () -> Unit) {
    val src: Persona? = vm.personas.find { it.id == personaId }
    if (src == null) {
        Scaffold(topBar = { TopAppBar(title = { Text("Персона") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") }
        }) }) { pad ->
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Персона удалена.")
            }
        }
        return
    }
    var name by remember(src.id) { mutableStateOf(src.name) }
    var prompt by remember(src.id) { mutableStateOf(src.systemPrompt) }
    var emoji by remember(src.id) { mutableStateOf(src.avatarEmoji) }
    var desc by remember(src.id) { mutableStateOf(src.desc) }
    var tags by remember(src.id) { mutableStateOf(src.tags.joinToString(", ")) }
    var temp by remember(src.id) { mutableStateOf(src.temperature) }
    var voice by remember(src.id) { mutableStateOf(src.voice) }
    var voiceMenu by remember { mutableStateOf(false) }
    var nsfw by remember(src.id) { mutableStateOf(src.nsfwAllowed) }
    var engine by remember(src.id) { mutableStateOf(src.engine) }
    var engMenu by remember { mutableStateOf(false) }

    fun engineLabel(id: String): String = when (id) {
        "" -> "Как везде"
        "local" -> "На телефоне"
        "mock" -> "Mock"
        else -> vm.providers.find { it.id == id }?.name ?: "?"
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Изменить: " + src.name) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") } },
            actions = {
                TextButton(onClick = {
                    vm.updatePersonaFull(src.id, name, prompt, emoji, desc, tags, temp, voice, nsfw, engine)
                    onBack()
                }) { Text("Готово") }
            })
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarView(src.avatarPath, emoji.ifBlank { src.avatarEmoji }, 64.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(emoji, { emoji = it }, label = { Text("Эмодзи") },
                        modifier = Modifier.weight(1f), singleLine = true)
                }
            }
            item {
                OutlinedTextField(name, { name = it }, label = { Text("Имя") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            item {
                OutlinedTextField(desc, { desc = it }, label = { Text("Описание для карточки") },
                    modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(prompt, { prompt = it }, label = { Text("Системный промпт") },
                    modifier = Modifier.fillMaxWidth(), minLines = 4)
            }
            item {
                OutlinedTextField(tags, { tags = it }, label = { Text("Теги через запятую") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            item {
                Text("Температура: " + "%.1f".format(temp))
                Slider(value = temp, onValueChange = { temp = it }, valueRange = 0f..1.5f, steps = 14,
                    modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Голос: " + voice.ifBlank { "общий" }, modifier = Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { voiceMenu = true }) { Text("Выбрать") }
                        DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
                            DropdownMenuItem(text = { Text("общий") }, onClick = { voice = ""; voiceMenu = false })
                            vm.voiceDirs.forEach { v ->
                                DropdownMenuItem(text = { Text(v) }, onClick = { voice = v; voiceMenu = false })
                            }
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Движок: " + engineLabel(engine), modifier = Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { engMenu = true }) { Text("Выбрать") }
                        DropdownMenu(expanded = engMenu, onDismissRequest = { engMenu = false }) {
                            DropdownMenuItem(text = { Text("Как везде") },
                                onClick = { engine = ""; engMenu = false })
                            DropdownMenuItem(text = { Text("На телефоне") },
                                onClick = { engine = "local"; engMenu = false })
                            DropdownMenuItem(text = { Text("Mock") },
                                onClick = { engine = "mock"; engMenu = false })
                            vm.providers.forEach { pr ->
                                DropdownMenuItem(
                                    text = { Text(pr.name + if (pr.enabled) "" else " (выкл)") },
                                    onClick = { engine = pr.id; engMenu = false })
                            }
                        }
                    }
                }
                Text("Свой движок + автозапасной из настроек.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = nsfw, onCheckedChange = { nsfw = it })
                    Text("18+ (без цензуры, только для взрослых)")
                }
            }
            item {
                Button(onClick = {
                    vm.updatePersonaFull(src.id, name, prompt, emoji, desc, tags, temp, voice, nsfw, engine)
                    onBack()
                }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
            }
        }
    }
}

/** Круглый стол: 2–4 персоны обсуждают тему по кругу. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundTableScreen(vm: AppViewModel, onBack: () -> Unit, onOpenChats: () -> Unit) {
    var topic by remember { mutableStateOf("") }
    var rounds by remember { mutableIntStateOf(2) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Круглый стол") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") } }
        )
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Участники (2–4):", style = MaterialTheme.typography.titleSmall)
            }
            items(vm.personas, key = { it.id }) { per ->
                val sel = per.id in vm.rtSelected
                ElevatedCard(
                    onClick = { vm.rtToggle(per.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = sel, onCheckedChange = { vm.rtToggle(per.id) })
                        AvatarView(per.avatarPath, per.avatarEmoji, 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(per.name, style = MaterialTheme.typography.titleSmall)
                            if (per.engine.isNotBlank()) {
                                Text(
                                    "движок: " + (vm.providers.find { it.id == per.engine }?.name ?: per.engine),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(topic, { topic = it }, label = { Text("Тема обсуждения…") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2)
                Spacer(Modifier.height(8.dp))
                Text("Кругов: $rounds")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..4).forEach { r ->
                        FilterChip(selected = rounds == r, onClick = { rounds = r }, label = { Text("$r") })
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (!vm.rtRunning) {
                    Button(
                        onClick = { vm.startRoundTable(topic, rounds) },
                        enabled = vm.rtSelected.size in 2..4 && topic.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Начать обсуждение") }
                } else {
                    Button(onClick = { vm.stopRoundTable() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Стоп")
                    }
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            items(vm.rtTurns) { turn ->
                val per = vm.personas.find { it.id == turn.personaId }
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp)) {
                        AvatarView(per?.avatarPath ?: "", per?.avatarEmoji ?: "\uD83D\uDE00", 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(turn.name, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(2.dp))
                            Text(turn.text)
                        }
                    }
                }
            }
            if (vm.rtTurns.isNotEmpty() && !vm.rtRunning) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            vm.saveRoundTable(topic.ifBlank { "без темы" })
                        }, modifier = Modifier.weight(1f)) { Text("В общий чат") }
                        OutlinedButton(onClick = { vm.rtClear() }, modifier = Modifier.weight(1f)) {
                            Text("Очистить")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = {
                        vm.startRoundTable(topic.ifBlank { "продолжаем" }, 2, append = true)
                    }, modifier = Modifier.fillMaxWidth()) { Text("Ещё круг (+2)") }
                    TextButton(onClick = onOpenChats, modifier = Modifier.fillMaxWidth()) {
                        Text("Открыть чаты")
                    }
                }
            }
        }
    }
}
