package app.moment.tracker.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Paper = Color(0xFFFAF8F4)
val Ink = Color(0xFF302F2A)
val Muted = Color(0xFF8C887D)
val Terracotta = Color(0xFFB75E3F)
val Sage = Color(0xFF627764)
val Line = Color(0xFFEAE5DC)

@Composable fun MomentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Terracotta, secondary = Sage, background = Paper,
            surface = Color.White, onBackground = Ink, onSurface = Ink, outlineVariant = Line,
            primaryContainer = Color(0xFFF3E3D9), secondaryContainer = Color(0xFFE8EDE4)),
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
            headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
            titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 21.sp),
            labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
        ), content = content
    )
}
