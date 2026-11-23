package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class VortexPalette(
    val cyan: Color = ElectricCyan,
    val iceCyan: Color = IceCyan,
    val violet: Color = NeonViolet,
    val brightViolet: Color = BrightViolet,
    val phantomMagenta: Color = PhantomMagenta,
    val incognitoCanvas: Color = IncognitoCanvas,
    val incognitoSurface: Color = IncognitoSurface,
    val shieldGreen: Color = ShieldEmerald,
    val amberPulse: Color = AmberPulse,
    val subtleOutline: Color = ElevatedOutline,
    val glassSurface: Color = MidnightGlass,
    val elevatedSurface: Color = SlateIndigo
)

val LocalVortexPalette = staticCompositionLocalOf { VortexPalette() }

private val VortexDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = DeepOnAccent,
    primaryContainer = Color(0xFF003B47),
    onPrimaryContainer = IceCyan,
    secondary = NeonViolet,
    onSecondary = AlabasterText,
    secondaryContainer = Color(0xFF331854),
    onSecondaryContainer = BrightViolet,
    tertiary = PhantomMagenta,
    onTertiary = AlabasterText,
    background = ObsidianCanvas,
    onBackground = AlabasterText,
    surface = MidnightGlass,
    onSurface = AlabasterText,
    surfaceVariant = SlateIndigo,
    onSurfaceVariant = MutedLavender,
    outline = ElevatedOutline,
    error = CrimsonAlert,
    onError = AlabasterText
)

private val VortexShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VortexTheme(
    content: @Composable () -> Unit
) {
    val typography = rememberVortexTypography()
    CompositionLocalProvider(LocalVortexPalette provides VortexPalette()) {
        MaterialTheme(
            colorScheme = VortexDarkColorScheme,
            typography = typography,
            shapes = VortexShapes,
            content = content
        )
    }
}
