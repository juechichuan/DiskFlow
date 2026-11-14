package com.diskflow.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF080D14)
val Panel = Color(0xFF101923)
val PanelRaised = Color(0xFF152230)
val Mint = Color(0xFF57E6C1)
val Lavender = Color(0xFF9EAFFF)
val TextPrimary = Color(0xFFEAF3F5)
val TextSecondary = Color(0xFF93A6B8)
val Warning = Color(0xFFFFC778)
val Error = Color(0xFFFF928B)

private val DiskFlowColors = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF03251D),
    primaryContainer = Color(0xFF153C34),
    onPrimaryContainer = Color(0xFFB9FFEB),
    secondary = Lavender,
    onSecondary = Color(0xFF131936),
    secondaryContainer = Color(0xFF283252),
    onSecondaryContainer = Color(0xFFE0E4FF),
    tertiary = Warning,
    onTertiary = Color(0xFF2B1B00),
    background = Ink,
    onBackground = TextPrimary,
    surface = Panel,
    onSurface = TextPrimary,
    surfaceVariant = PanelRaised,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = Color(0xFF0D151F),
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelRaised,
    outline = Color(0xFF314354),
    outlineVariant = Color(0xFF223140),
    error = Error,
    onError = Color(0xFF3A0907),
    errorContainer = Color(0xFF48201E),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val DiskFlowTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun DiskFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DiskFlowColors,
        typography = DiskFlowTypography,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
        ),
        content = content,
    )
}
