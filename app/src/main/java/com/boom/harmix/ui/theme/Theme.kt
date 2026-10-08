package com.boom.harmix.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = SunsetGold,
    onPrimary = MidnightBlack,
    secondary = AmberGlow,
    onSecondary = MidnightBlack,
    tertiary = EmberRed,
    background = MidnightBlack,
    onBackground = Bone,
    surface = SurfaceCoal,
    onSurface = Bone,
    surfaceVariant = GlassFill,
    onSurfaceVariant = Sand,
    outline = GlassBorder
)

@Composable
fun HarmixTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = MidnightBlack.toArgb()
            window.navigationBarColor = MidnightBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = HarmixTypography, // <-- The Exo 2 Font is finally applied!
        content = content
    )
}
