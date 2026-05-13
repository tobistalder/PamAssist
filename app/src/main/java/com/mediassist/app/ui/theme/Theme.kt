package com.mediassist.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ─── Typography ───────────────────────────────────────────────────────────────
val PamFontFamily = FontFamily.Default

val PamTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = PamFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        letterSpacing = (-0.5).sp,
        color = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = PamFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = (-0.3).sp,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = PamFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = PamFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = TextPrimary
    ),
    titleSmall = TextStyle(
        fontFamily = PamFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = TextSecondary
    ),
    bodyLarge = TextStyle(
        fontFamily = PamFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = PamFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextSecondary
    ),
    bodySmall = TextStyle(
        fontFamily = PamFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = TextHint
    ),
    labelLarge = TextStyle(
        fontFamily = PamFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp,
        color = TextOnPrimary
    ),
    labelMedium = TextStyle(
        fontFamily = PamFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp,
        color = TextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = PamFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.5.sp,
        color = TextHint
    ),
)

// ─── Color Schemes ────────────────────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary             = TealPrimary,
    onPrimary           = TextOnPrimary,
    primaryContainer    = TealLight,
    onPrimaryContainer  = TealDark,

    secondary           = SagePrimary,
    onSecondary         = TextOnSage,
    secondaryContainer  = SageLight,
    onSecondaryContainer = SageDark,

    tertiary            = TealDark,
    onTertiary          = TextOnPrimary,
    tertiaryContainer   = TealContainer,
    onTertiaryContainer = TealDark,

    error               = ErrorRed,
    onError             = Color.White,
    errorContainer      = ErrorRedLight,
    onErrorContainer    = ErrorRed,

    background          = Background,       // #FAF2E1
    onBackground        = TextPrimary,
    surface             = CardBackground,   // #FFFFFF para cards
    onSurface           = TextPrimary,
    surfaceVariant      = Surface,          // #F2E8D0 para bottom sheets / chips
    onSurfaceVariant    = TextSecondary,

    outline             = DividerColor,
    outlineVariant      = BorderColor,
    scrim               = Scrim,
    inverseSurface      = TealDark,
    inverseOnSurface    = TextOnPrimary,
    inversePrimary      = TealLight,
)

private val DarkColorScheme = darkColorScheme(
    primary             = TealLight,
    onPrimary           = TealDark,
    primaryContainer    = TealDark,
    onPrimaryContainer  = TealLight,

    secondary           = SageLight,
    onSecondary         = SageDark,
    secondaryContainer  = SageDark,
    onSecondaryContainer = SageLight,

    tertiary            = TealContainer,
    onTertiary          = TealDark,
    tertiaryContainer   = TealDark,
    onTertiaryContainer = TealLight,

    error               = ErrorRed,
    onError             = Color.White,
    errorContainer      = Color(0xFF5C1010),
    onErrorContainer    = ErrorRed,

    background          = Color(0xFF111A18),  // oscuro cálido
    onBackground        = Color(0xFFE8DFD0),
    surface             = Color(0xFF1C2B28),
    onSurface           = Color(0xFFE8DFD0),
    surfaceVariant      = Color(0xFF243530),
    onSurfaceVariant    = TextHint,

    outline             = Color(0xFF3A4F48),
    outlineVariant      = Color(0xFF2A3D38),
    scrim               = Scrim,
    inverseSurface      = Color(0xFFE8DFD0),
    inverseOnSurface    = TealDark,
    inversePrimary      = TealPrimary,
)

// ─── PamAssistTheme ───────────────────────────────────────────────────────────
@Composable
fun PamAssistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = PamTypography,
        content     = content
    )
}
