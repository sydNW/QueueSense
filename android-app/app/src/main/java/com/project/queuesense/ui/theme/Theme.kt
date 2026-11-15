package com.project.queuesense.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val QueueSenseColorScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealPale,
    onPrimaryContainer = TealDark,
    secondary = Navy,
    onSecondary = Color.White,
    background = PageBackground,
    onBackground = TextDark,
    surface = CardWhite,
    onSurface = TextDark,
    surfaceVariant = TealPale,
    onSurfaceVariant = TextMuted,
    outline = OutlineGrey
)

@Composable
fun QueueSenseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = QueueSenseColorScheme,
        typography = Typography,
        content = content
    )
}