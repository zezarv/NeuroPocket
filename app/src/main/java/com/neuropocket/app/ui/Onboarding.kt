package com.neuropocket.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuropocket.app.AppViewModel
import com.neuropocket.app.data.ProviderPresets

/** Мастер первого запуска: выбор движка за 3 шага. */
@Composable
fun OnboardingScreen(
    vm: AppViewModel,
    onOpenModels: () -> Unit,
    onOpenProviders: () -> Unit,
    onDone: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.weight(0.3f))
            Text("NeuroPocket", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            when (step) {
                0 -> {
                    Text("Локальный ИИ-хаб на твоём телефоне.", fontSize = 18.sp)
                    Text(
                        "• Чат, персоны и лента работают даже без интернета\n" +
                            "• Модели качаются один раз и живут на устройстве\n" +
                            "• Можно подключить ПК (LM Studio, Ollama) или API",
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Дальше") }
                    TextButton(onClick = { vm.markOnboarded(); onDone() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Пропустить")
                    }
                }
                1 -> {
                    Text("Откуда брать ум?", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    EnginePickCard(
                        icon = Icons.Default.Smartphone,
                        title = "С телефона",
                        sub = "Скачай GGUF 1–3B. Медленнее, но приватно и офлайн.",
                        action = "Выбрать модель"
                    ) { onOpenModels() }
                    EnginePickCard(
                        icon = Icons.Default.Computer,
                        title = "С моего ПК",
                        sub = "LM Studio / Ollama по Wi-Fi. Быстро и бесплатно.",
                        action = "Настроить"
                    ) { onOpenProviders() }
                    EnginePickCard(
                        icon = Icons.Default.Cloud,
                        title = "Бесплатное облако",
                        sub = "Pollinations — без ключа и регистрации.",
                        action = if (vm.providers.any { it.kind == "pollinations" }) "Уже добавлено ✓" else "Добавить в 1 тап"
                    ) {
                        if (vm.providers.none { it.kind == "pollinations" }) {
                            ProviderPresets.cloud.firstOrNull { it.kind == "pollinations" }?.let { vm.addPreset(it) }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { step = 0 }, modifier = Modifier.weight(1f)) { Text("Назад") }
                        Button(onClick = { step = 2 }, modifier = Modifier.weight(1f)) { Text("Дальше") }
                    }
                }
                else -> {
                    Text("Всё готово", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Сейчас: ${vm.engineLabel()}", color = MaterialTheme.colorScheme.secondary)
                    Text(
                        "Советы:\n" +
                            "• Хаб — все инструменты\n" +
                            "• У каждой функции своя история\n" +
                            "• Бенчмарк в Моделях покажет скорость\n" +
                            "• Это обучение можно вернуть в Настройках",
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { vm.markOnboarded(); onDone() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Начать")
                    }
                }
            }
            Spacer(Modifier.weight(0.3f))
        }
    }
}

@Composable
private fun EnginePickCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    sub: String,
    action: String,
    onClick: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(sub, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary)
            }
        }
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            Text(action)
        }
        Spacer(Modifier.height(10.dp))
    }
}
