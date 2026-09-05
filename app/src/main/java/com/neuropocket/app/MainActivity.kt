package com.neuropocket.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neuropocket.app.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: android.content.Intent) {
        when (intent.action) {
            "np.action.CHAT" -> {
                vm.fireRoute("chat")
                return
            }
            "np.action.VOICE" -> {
                vm.fireRoute("tools:voice")
                return
            }
        }
        if (intent.action != android.content.Intent.ACTION_SEND) return
        val type = intent.type ?: return
        when {
            type.startsWith("text/") -> {
                intent.getStringExtra(android.content.Intent.EXTRA_TEXT)?.let { vm.handleSharedText(it) }
            }
            type.startsWith("image/") -> {
                (intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
                    ?: intent.data)?.let { vm.handleSharedImage(it) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShare(intent)
        setContent {
            NeuroTheme(theme = vm.theme, accentHex = vm.accent) {
                // Системные бары в цвет темы (иначе белые полосы сверху/снизу)
                val darkBars = when (vm.theme) {
                    "dark" -> true
                    "light" -> false
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                }
                val sysView = androidx.compose.ui.platform.LocalView.current
                val barColor = MaterialTheme.colorScheme.surface.toArgb()
                val darkIcons = !darkBars
                androidx.compose.runtime.SideEffect {
                    try {
                        val w = (sysView.context as android.app.Activity).window
                        w.statusBarColor = barColor
                        w.navigationBarColor = barColor
                        androidx.core.view.WindowInsetsControllerCompat(w, sysView).apply {
                            isAppearanceLightStatusBars = darkIcons
                            isAppearanceLightNavigationBars = darkIcons
                        }
                    } catch (_: Exception) { }
                }
                var tab by remember { mutableIntStateOf(0) }
                var hubRoute by remember { mutableStateOf<String?>(null) }
                var overlay by remember { mutableStateOf<String?>(null) }
                var drawerFind by remember { mutableStateOf("") }
                var moveFor by remember { mutableStateOf<String?>(null) }
                var menuFor by remember { mutableStateOf<String?>(null) }
                var moveName by remember { mutableStateOf("") }
                if (!vm.onboarded) {
                    OnboardingScreen(
                        vm,
                        onOpenModels = { vm.markOnboarded(); overlay = null; hubRoute = null; tab = 4 },
                        onOpenProviders = { vm.markOnboarded(); overlay = null; hubRoute = "providers"; tab = 0 },
                        onDone = {}
                    )
                    return@NeuroTheme
                }
                val drawer = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                // Back: сначала overlay, потом маршрут хаба
                BackHandler(enabled = overlay != null) { overlay = null }
                BackHandler(enabled = overlay == null && tab == 0 && hubRoute != null) { hubRoute = null }

                // Автожизнь моделей: вход/выход из зон чата и инструментов
                val zone = when {
                    overlay?.startsWith("tool:") == true -> "chat"
                    overlay?.startsWith("pchat:") == true -> "chat"
                    tab == 1 && overlay == null -> "chat"
                    tab == 0 && overlay == null &&
                        (hubRoute == "tools" || hubRoute?.startsWith("tools:") == true) -> "tools"
                    else -> null
                }
                var lastZone by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(zone) {
                    val prev = lastZone
                    lastZone = zone
                    if (prev != null && prev != zone) vm.onLeaveScreen(prev)
                    if (zone != null) vm.onEnterScreen(zone)
                }
                LaunchedEffect(vm.shareTarget) {
                    when (vm.consumeShareTarget()) {
                        "chat" -> { overlay = null; hubRoute = null; tab = 1 }
                        "tools:vision" -> { overlay = null; hubRoute = "tools:vision"; tab = 0 }
                    }
                }

                val items = listOf(
                    Nav("Хаб", Icons.Default.Dashboard),
                    Nav("Чаты", Icons.Default.Chat),
                    Nav("Персоны", Icons.Default.Person),
                    Nav("Лента", Icons.Default.Home),
                    Nav("Модели", Icons.Default.CloudDownload)
                )

                fun openTab(i: Int) { overlay = null; hubRoute = null; tab = i }
                fun openTool(id: String) { overlay = "tool:$id" }
                fun openPersona(id: String) { overlay = "persona:$id" }
                fun openPChat(id: String) { overlay = "pchat:$id" }

                ModalNavigationDrawer(
                    drawerState = drawer,
                    gesturesEnabled = overlay == null && (tab == 0 || tab == 1),
                    drawerContent = {
                        ModalDrawerSheet {
                            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("NeuroPocket", fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f))
                                Text(vm.appVersion.ifBlank { "" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary)
                            }
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Чаты", style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    vm.newChat(); scope.launch { drawer.close() }; openTab(1)
                                }) { Icon(Icons.Default.Add, contentDescription = "Новый чат") }
                            }
                            OutlinedTextField(drawerFind, { drawerFind = it },
                                label = { Text("Найти чат…") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                singleLine = true)
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                val base = if (drawerFind.isBlank()) vm.sessions
                                else vm.sessions.filter { it.title.contains(drawerFind, true) }
                                val groups = linkedMapOf<String, List<com.neuropocket.app.data.ChatSession>>()
                                val pinned = base.filter { it.pinned }
                                if (pinned.isNotEmpty()) groups["Закреп"] = pinned
                                val rest = base.filter { !it.pinned }
                                rest.filter { it.folder.isNotBlank() }.groupBy { it.folder }
                                    .toSortedMap().forEach { (k, v) -> groups["Папка: " + k] = v }
                                rest.filter { it.folder.isBlank() }.groupBy { sessionDayGroup(it.updated) }
                                    .forEach { (k, v) -> groups[k] = v }
                                val order = listOf("Закреп") +
                                    groups.keys.filter { it.startsWith("Папка: ") }.sorted() +
                                    listOf("Сегодня", "Вчера", "Ранее")
                                order.forEach { g ->
                                    val list = groups[g] ?: return@forEach
                                    item {
                                        Text(g, style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                                    }
                                    items(list, key = { it.id }) { s ->
                                        val sel = s.id == vm.activeSessionId
                                        NavigationDrawerItem(
                                            label = { Text(s.title.ifBlank { "Без названия" }, maxLines = 1) },
                                            selected = sel,
                                            onClick = {
                                                vm.openSession(s.id); scope.launch { drawer.close() }
                                                openTab(1)
                                            },
                                            badge = {
                                                Box {
                                                    IconButton(onClick = { menuFor = s.id },
                                                        modifier = Modifier.size(32.dp)) {
                                                        Icon(Icons.Default.MoreVert, contentDescription = "Ещё",
                                                            modifier = Modifier.size(18.dp))
                                                    }
                                                    DropdownMenu(
                                                        expanded = menuFor == s.id,
                                                        onDismissRequest = { menuFor = null }
                                                    ) {
                                                        DropdownMenuItem(
                                                            text = { Text(if (s.pinned) "Открепить" else "Закрепить") },
                                                            onClick = { vm.togglePin(s.id); menuFor = null })
                                                        DropdownMenuItem(
                                                            text = { Text("В папку…") },
                                                            onClick = { moveFor = s.id; moveName = s.folder; menuFor = null })
                                                        DropdownMenuItem(
                                                            text = { Text("Удалить") },
                                                            onClick = { vm.deleteSession(s.id); menuFor = null })
                                                    }
                                                }
                                            },
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    }
                                }
                            }
                            if (moveFor != null) {
                                AlertDialog(
                                    onDismissRequest = { moveFor = null },
                                    title = { Text("Папка чата") },
                                    text = {
                                        Column {
                                            OutlinedTextField(moveName, { moveName = it },
                                                label = { Text("Имя папки (пусто — без папки)") },
                                                modifier = Modifier.fillMaxWidth(), singleLine = true)
                                            Spacer(Modifier.height(8.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                vm.folders().filter { it.isNotBlank() }.take(6).forEach { f ->
                                                    FilterChip(selected = moveName == f,
                                                        onClick = { moveName = f }, label = { Text(f) })
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        Button(onClick = {
                                            moveFor?.let { vm.moveSession(it, moveName) }
                                            moveFor = null
                                        }) { Text("Ок") }
                                    },
                                    dismissButton = { TextButton(onClick = { moveFor = null }) { Text("Отмена") } }
                                )
                            }
                        }
                    }
                ) {
                    Scaffold(bottomBar = {
                        NavigationBar {
                            items.forEachIndexed { i, n ->
                                NavigationBarItem(selected = tab == i && overlay == null,
                                    onClick = { openTab(i) },
                                    icon = { Icon(n.icon, contentDescription = n.label) },
                                    label = { Text(n.label) })
                            }
                        }
                    }) { pad ->
                        Surface(Modifier.padding(pad)) {
                            // Overlay-экраны поверх всего
                            val ov = overlay
                            if (ov != null) {
                                when {
                                    ov.startsWith("persona:") -> PersonaDetailScreen(
                                        vm, ov.removePrefix("persona:"),
                                        onBack = { overlay = null },
                                        onOpenChat = { openPChat(it) },
                                        onEdit = { overlay = "pedit:$it" })
                                    ov.startsWith("pedit:") -> PersonaEditScreen(
                                        vm, ov.removePrefix("pedit:"),
                                        onBack = { overlay = "persona:" + ov.removePrefix("pedit:") })
                                    ov.startsWith("pchat:") -> PersonaChatScreen(
                                        vm, ov.removePrefix("pchat:"),
                                        onBack = { overlay = null })
                                    ov == "round" -> RoundTableScreen(
                                        vm,
                                        onBack = { overlay = null },
                                        onOpenChats = { overlay = null; openTab(1) })
                                    ov.startsWith("tool:") -> ToolEnvScreen(
                                        vm, ov.removePrefix("tool:"),
                                        onBack = { overlay = null },
                                        onDiscuss = { overlay = null; openTab(1); vm.discussInChat(it) })
                                }
                                return@Surface
                            }
                            when (tab) {
                                0 -> when {
                                    hubRoute == null -> HubScreen(
                                        vm,
                                        onNewChat = { vm.newChat(); tab = 1 },
                                        onHubRoute = { hubRoute = it },
                                        onOpenTab = { openTab(it) },
                                        onOpenSession = { vm.openSession(it); tab = 1 },
                                        onOpenTool = { openTool(it) },
                                        onOpenPersona = { openPersona(it) }
                                    )
                                    hubRoute == "providers" -> ProvidersScreen(vm,
                                        onBack = { hubRoute = null })
                                    hubRoute == "diag" -> DiagScreen(vm,
                                        onBack = { hubRoute = null })
                                    hubRoute == "settings" -> SettingsScreen(vm,
                                        onOpenTab = { openTab(it) },
                                        onOpenPersonas = { openTab(2) },
                                        onOpenProviders = { hubRoute = "providers" },
                                        onOpenDiag = { hubRoute = "diag" })
                                    else -> ToolsScreen(vm,
                                        onBack = { hubRoute = null },
                                        onOpenChats = { hubRoute = null; overlay = null; tab = 1 },
                                        initialCard = hubRoute?.substringAfter(":", "") ?: "",
                                        onOpenTool = { openTool(it) })
                                }
                                1 -> ChatScreen(vm, onMenu = { scope.launch { drawer.open() } })
                                2 -> PersonasScreen(vm,
                                    onOpenPersona = { openPersona(it) },
                                    onOpenChat = { openPChat(it) },
                                    onOpenRound = { overlay = "round" })
                                3 -> SocialScreen(vm, onOpenPersona = { openPersona(it) })
                                else -> ModelsScreen(vm, onOpenProviders = { hubRoute = "providers"; tab = 0 })
                            }
                        }
                    }
                }
            }
        }
    }
    data class Nav(val label: String, val icon: ImageVector)
}
