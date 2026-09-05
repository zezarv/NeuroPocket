package com.neuropocket.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Палитра в духе референсов: глубокий чёрный + золото. Акцент переопределяем из настроек.
private fun accentColor(hex: String, dark: Boolean): Color {
    return try { Color(android.graphics.Color.parseColor(hex)) }
    catch (_: Exception) { if (dark) Color(0xFFD9A441) else Color(0xFF6B4EFF) }
}

private fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color(0xFF141414),
    primaryContainer = Color(0xFF2A2417),
    secondary = Color(0xFF9A93A6),
    background = Color(0xFF0C0C12),
    onBackground = Color(0xFFF2EFE6),
    surface = Color(0xFF14141B),
    onSurface = Color(0xFFF2EFE6),
    surfaceVariant = Color(0xFF1E1E28),
    onSurfaceVariant = Color(0xFFC9C3D4),
    surfaceContainerHighest = Color(0xFF262633),
    outline = Color(0xFF3A3A48),
    error = Color(0xFFE57373)
)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    background = Color(0xFFF7F4EC),
    onBackground = Color(0xFF191924),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191924),
    surfaceVariant = Color(0xFFF0EBDD),
    onSurfaceVariant = Color(0xFF4A4458),
    outline = Color(0xFFD8D2C2)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun NeuroTheme(theme: String, accentHex: String, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    // По умолчанию — золото как в референсах; пользовательский акцент уважаем.
    val accent = accentColor(if (accentHex.isBlank()) "#D9A441" else accentHex, dark)
    val scheme = if (dark) darkScheme(accent) else lightScheme(accent)
    MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
}
