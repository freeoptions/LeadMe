package com.leadme.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object MapStyle {
    val home = Color(0xFF396958)
    val metro = Color(0xFF5A68A5)
    val bus = Color(0xFFA66636)
    val card = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 12.dp)
    val markerSize = 34.dp
    val touchSize = 48.dp
    val roadWidth = 14.dp
    val roadInset = 10.dp
    val labelWidth = 144.dp
    val labelOffset = 46.dp
    val canvasPadding = 20.dp
}

private val LightColors = lightColorScheme(
    primary = MapStyle.home, onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEAE2), onPrimaryContainer = Color(0xFF203C32),
    secondary = MapStyle.metro, tertiary = MapStyle.bus,
    background = Color(0xFFF7F8F2), surface = Color(0xFFF7F8F2),
    surfaceContainer = Color(0xFFEEF1E9), surfaceVariant = Color(0xFFE8ECE3),
    onSurface = Color(0xFF222F29), onSurfaceVariant = Color(0xFF606C64),
    outline = Color(0xFF839086), outlineVariant = Color(0xFFD6DED3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA7D1BD), onPrimary = Color(0xFF16382A),
    primaryContainer = Color(0xFF2D4D3E), onPrimaryContainer = Color(0xFFDDEAE2),
    secondary = Color(0xFFBBC4F5), tertiary = Color(0xFFE5B28B),
    background = Color(0xFF141D18), surface = Color(0xFF141D18),
    surfaceContainer = Color(0xFF202C24), surfaceVariant = Color(0xFF2E3A32),
    onSurface = Color(0xFFE5EAE3), onSurfaceVariant = Color(0xFFAFBBAF),
    outline = Color(0xFF849486), outlineVariant = Color(0xFF3B4B3F),
)

@Composable
fun LeadMeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(
            displayMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium,
                fontSize = 48.sp, letterSpacing = (-1.8).sp),
            headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 40.sp),
            titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
        ),
        shapes = Shapes(medium = RoundedCornerShape(16.dp), large = MapStyle.card),
        content = content,
    )
}
