package fr.passerelle.client.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

object Layout {
    val maxContentWidth = 1440.dp
    val maxFormWidth = 440.dp
}

/**
 * Passerelle Core Light Color Scheme.
 * Anchored around trusted cobalt primary, cool-toned slate neutrals, crisp white cards,
 * icy-blue tonal containers, emerald operational success, and high-contrast danger roles.
 */
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1D4ED8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE1FF),
    onPrimaryContainer = Color(0xFF001551),
    secondary = Color(0xFF0051D5),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE1FF),
    onSecondaryContainer = Color(0xFF00174B),
    tertiary = Color(0xFF004F35),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF002114),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF0B1C30),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF0B1C30),
    surfaceVariant = Color(0xFFD3E4FE),
    onSurfaceVariant = Color(0xFF434655),
    outline = Color(0xFF747686),
    outlineVariant = Color(0xFFC4C5D7),
    surfaceDim = Color(0xFFCBDBF5),
    surfaceBright = Color(0xFFF8F9FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF4FF),
    surfaceContainer = Color(0xFFE5EEFF),
    surfaceContainerHigh = Color(0xFFDCE9FF),
    surfaceContainerHighest = Color(0xFFD3E4FE),
    inverseSurface = Color(0xFF213145),
    inverseOnSurface = Color(0xFFEAF1FF),
    inversePrimary = Color(0xFFB7C4FF),
    surfaceTint = Color(0xFF2151DA),
)

/**
 * Passerelle Core Dark Color Scheme.
 * Preserves the same cobalt + cool slate hierarchy with high-legibility dark surfaces.
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB7C4FF),
    onPrimary = Color(0xFF00247B),
    primaryContainer = Color(0xFF0037B0),
    onPrimaryContainer = Color(0xFFDCE1FF),
    secondary = Color(0xFFB4C5FF),
    onSecondary = Color(0xFF00297A),
    secondaryContainer = Color(0xFF003EA8),
    onSecondaryContainer = Color(0xFFDBE1FF),
    tertiary = Color(0xFF68DBA9),
    onTertiary = Color(0xFF003824),
    tertiaryContainer = Color(0xFF005137),
    onTertiaryContainer = Color(0xFF85F8C4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0B1320),
    onBackground = Color(0xFFE5EEFF),
    surface = Color(0xFF0B1320),
    onSurface = Color(0xFFE5EEFF),
    surfaceVariant = Color(0xFF213145),
    onSurfaceVariant = Color(0xFFC4C5D7),
    outline = Color(0xFF8E90A0),
    outlineVariant = Color(0xFF324156),
    surfaceDim = Color(0xFF080E18),
    surfaceBright = Color(0xFF1E2C44),
    surfaceContainerLowest = Color(0xFF080E18),
    surfaceContainerLow = Color(0xFF101A2B),
    surfaceContainer = Color(0xFF162236),
    surfaceContainerHigh = Color(0xFF1E2C44),
    surfaceContainerHighest = Color(0xFF273752),
    inverseSurface = Color(0xFFE5EEFF),
    inverseOnSurface = Color(0xFF0B1C30),
    inversePrimary = Color(0xFF1D4ED8),
    surfaceTint = Color(0xFFB7C4FF),
)

val PasserelleShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

val PasserelleTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineSmall = TextStyle(
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
)

@Composable
expect fun platformColorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme?

@Composable
fun PasserelleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = platformColorScheme(darkTheme = darkTheme, dynamicColor = dynamicColor)
        ?: if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PasserelleTypography,
        shapes = PasserelleShapes,
        content = content,
    )
}
