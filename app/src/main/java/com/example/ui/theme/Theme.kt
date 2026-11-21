package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val RoninDarkColorScheme = darkColorScheme(
    primary = NeonPurple,
    onPrimary = VoidBlack,
    primaryContainer = DeepViolet,
    onPrimaryContainer = NeonPurpleBright,
    secondary = CyberCyan,
    onSecondary = VoidBlack,
    secondaryContainer = Color(0xFF0E3A47),
    onSecondaryContainer = CyberCyan,
    tertiary = OnionBeforeRose,
    onTertiary = TextPrimary,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = StudioBorder,
    outlineVariant = StudioSurfaceHighlight,
    error = OnionBeforeRose,
    onError = TextPrimary
)

val RoninShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RoninStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RoninDarkColorScheme,
        typography = Typography,
        shapes = RoninShapes,
        content = content
    )
}
