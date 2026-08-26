package app.radiorecalarm.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val KellraadioShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

val KellraadioDarkColorScheme = darkColorScheme(
    primary = AmberPrimary,
    primaryContainer = AmberContainer,
    onPrimaryContainer = OnAmberContainer,
    onPrimary = Color.Black,
    secondary = AmberLight,
    onSecondary = AmberGold,
    secondaryContainer = Color(0xFF202434),
    onSecondaryContainer = TitaniumWhite,
    tertiary = AmberSunrise,
    onTertiary = Color.Black,
    background = TitaniumCanvas,
    surface = TitaniumSurface,
    surfaceVariant = TitaniumCard,
    onSurface = TitaniumWhite,
    onSurfaceVariant = TitaniumTextSecondary,
    outline = TitaniumCardBorder,
    outlineVariant = TitaniumDivider,
    error = StudioRecRed,
    onError = Color.White
)

@Composable
fun KellraadioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = KellraadioDarkColorScheme,
        typography = Typography,
        shapes = KellraadioShapes,
        content = content
    )
}
