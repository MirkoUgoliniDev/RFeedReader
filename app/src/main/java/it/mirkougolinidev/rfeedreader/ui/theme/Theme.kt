package it.mirkougolinidev.rfeedreader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AccentGreen = Color(0xFF20E6A0)
private val Background = Color(0xFF101216)
private val Surface = Color(0xFF1B1F25)
private val TextPrimary = Color(0xFFF5F7FA)
private val TextSecondary = Color(0xFFB9C0C9)

private val DarkColors = darkColorScheme(
    primary = AccentGreen,
    onPrimary = Color(0xFF002116),
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF292F37),
    onSurfaceVariant = TextSecondary,
    secondary = Color(0xFF7BEFC5)
)

private val RFeedReaderTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp)
)

@Composable
fun RFeedReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = RFeedReaderTypography,
        content = content
    )
}
