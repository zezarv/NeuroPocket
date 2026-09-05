package com.neuropocket.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuropocket.app.AppViewModel

data class HubTile(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit
)

@Composable
fun HubScreen(
    vm: AppViewModel,
    onNewChat: () -> Unit,
    onHubRoute: (String) -> Unit,
    onOpenTab: (Int) -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenTool: (String) -> Unit,
    onOpenPersona: (String) -> Unit
) {
    val tiles = listOf(
        HubTile("Переводчик", Icons.Default.Translate, Color(0xFF3E6B8C)) { onOpenTool("translator") },
        HubTile("Транскрибатор", Icons.Default.Mic, Color(0xFF3E7C4F)) { onHubRoute("tools:transcriber") },
        HubTile("Детектор", Icons.Default.Shield, Color(0xFF8C4A3E)) { onOpenTool("detector") },
        HubTile("Генератор фото", Icons.Default.Palette, Color(0xFF6B4E8C)) { onHubRoute("tools:photo") },
        HubTile("VibeCode", Icons.Default.Code, Color(0xFF4A4A5E)) { onOpenTool("vibecode") },
        HubTile("Улучшение", Icons.Default.AutoFixHigh, Color(0xFF2E7C74)) { onOpenTool("improver") },
        HubTile("Саммари", Icons.Default.Notes, Color(0xFF3E7C6B)) { onOpenTool("summarizer") },
        HubTile("Агент", Icons.Default.SmartToy, Color(0xFF8C6B3E)) { onHubRoute("tools:agent") },
        HubTile("Песочница", Icons.Default.Science, Color(0xFF55556A)) { onHubRoute("tools") },
        HubTile("Персоны", Icons.Default.Person, Color(0xFF8C4E6E)) { onOpenTab(2) },
        HubTile("Лента", Icons.Default.Home, Color(0xFF3E5A8C)) { onOpenTab(3) },
        HubTile("Модели", Icons.Default.CloudDownload, Color(0xFF5E5E3E)) { onOpenTab(4) },
        HubTile("Голосовой чат", Icons.Default.RecordVoiceOver, Color(0xFF2E6B5E)) { onHubRoute("tools:voice") },
        HubTile("Фото-вопрос", Icons.Default.PhotoCamera, Color(0xFF6B5E3E)) { onHubRoute("tools:vision") },
        HubTile("Заметки", Icons.Default.NoteAlt, Color(0xFF5E6B3E)) { onHubRoute("tools:rag") },
        HubTile("ПК и API", Icons.Default.Dns, Color(0xFF3E6B5E)) { onHubRoute("providers") }
    )

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("NeuroPocket", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(vm.engineLabel() + " • офлайн", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary)
                }
                AssistChip(onClick = { onOpenTab(4) }, label = { Text(vm.nativeInfo) })
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { onHubRoute("settings") }) {
                    Icon(Icons.Default.Settings, contentDescription = "Настройки")
                }
            }
        }
        item {
            // Hero: лёгкий градиент от акцента к поверхности
            val heroBrush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                    MaterialTheme.colorScheme.surfaceVariant
                )
            )
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onNewChat() },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(heroBrush, MaterialTheme.shapes.large)
                ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) {
                                Icon(Icons.Default.Chat, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(10.dp).size(26.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text("ИИ Чат", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(vm.activePersona?.name ?: "…", color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onNewChat,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary)) {
                            Text("Начать чат")
                        }
                    }
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        modifier = Modifier.size(110.dp))
                }
                }
            }
        }
        item { Text("Инструменты", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tiles.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { t ->
                            Card(
                                modifier = Modifier.weight(1f).clickable { t.onClick() },
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = t.tint.copy(alpha = 0.55f))
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Surface(shape = MaterialTheme.shapes.small, color = Color.White.copy(alpha = 0.14f)) {
                                        Icon(t.icon, contentDescription = null, tint = Color.White,
                                            modifier = Modifier.padding(8.dp).size(22.dp))
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Text(t.label, color = Color.White, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (vm.sessions.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Недавние чаты", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onNewChat) { Text("+ Новый") }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.sessions.take(4).forEach { s ->
                        ElevatedCard(onClick = { onOpenSession(s.id) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text(s.title.ifBlank { "Без названия" }, modifier = Modifier.weight(1f),
                                    maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Группировка сессий для drawer: сегодня / вчера / ранее. */
fun sessionDayGroup(ts: Long): String {
    val cal = java.util.Calendar.getInstance()
    val today = cal.apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
    return when {
        ts >= today -> "Сегодня"
        ts >= today - 86400000L -> "Вчера"
        else -> "Ранее"
    }
}
