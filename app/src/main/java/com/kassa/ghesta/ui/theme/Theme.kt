package com.kassa.ghesta.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.view.WindowCompat
import com.kassa.ghesta.R

val Vazir = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

@Immutable
class Palette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val line: Color,
    val gold: Color,
    val gold2: Color,
    val teal: Color,
    val teal2: Color,
    val tealDeep: Color,
    val primary: Color,
    val onPrimary: Color,
    val danger: Color,
    val dangerBg: Color,
    val warn: Color,
    val warnBg: Color,
    val ok: Color,
    val okBg: Color,
)

val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFF6F2EA),
    surface = Color(0xFFFFFDF9),
    surface2 = Color(0xFFF1ECE1),
    ink = Color(0xFF14272B),
    ink2 = Color(0xFF4C5D60),
    muted = Color(0xFF8A979A),
    line = Color(0x1A14272B),
    gold = Color(0xFFB8923F),
    gold2 = Color(0xFFE2C070),
    teal = Color(0xFF0E3B43),
    teal2 = Color(0xFF1B5F6A),
    tealDeep = Color(0xFF082A31),
    primary = Color(0xFF0E3B43),
    onPrimary = Color(0xFFFFFFFF),
    danger = Color(0xFFC23B4A),
    dangerBg = Color(0xFFFBE9EA),
    warn = Color(0xFFB36B00),
    warnBg = Color(0xFFFDF1DC),
    ok = Color(0xFF1F8A63),
    okBg = Color(0xFFE2F3EC),
)

val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF0B1417),
    surface = Color(0xFF122025),
    surface2 = Color(0xFF1A2D33),
    ink = Color(0xFFF3EFE6),
    ink2 = Color(0xFFB4C0C2),
    muted = Color(0xFF7B8E91),
    line = Color(0x1FFFFFFF),
    gold = Color(0xFFD9B668),
    gold2 = Color(0xFFE8CB84),
    teal = Color(0xFF0E3B43),
    teal2 = Color(0xFF1B5F6A),
    tealDeep = Color(0xFF082A31),
    primary = Color(0xFFE2C070),
    onPrimary = Color(0xFF0B1417),
    danger = Color(0xFFF2707D),
    dangerBg = Color(0x29F2707D),
    warn = Color(0xFFEBAE50),
    warnBg = Color(0x29EBAE50),
    ok = Color(0xFF52CBA0),
    okBg = Color(0x2952CBA0),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

object G {
    val c: Palette
        @Composable @ReadOnlyComposable get() = LocalPalette.current
}

private fun vazirTypography(): Typography {
    val base = Typography()
    fun TextStyle.v() = copy(fontFamily = Vazir)
    return Typography(
        displayLarge = base.displayLarge.v(), displayMedium = base.displayMedium.v(), displaySmall = base.displaySmall.v(),
        headlineLarge = base.headlineLarge.v(), headlineMedium = base.headlineMedium.v(), headlineSmall = base.headlineSmall.v(),
        titleLarge = base.titleLarge.v(), titleMedium = base.titleMedium.v(), titleSmall = base.titleSmall.v(),
        bodyLarge = base.bodyLarge.v(), bodyMedium = base.bodyMedium.v(), bodySmall = base.bodySmall.v(),
        labelLarge = base.labelLarge.v(), labelMedium = base.labelMedium.v(), labelSmall = base.labelSmall.v(),
    )
}

/** [mode]: 0 = follow system, 1 = light, 2 = dark. */
@Composable
fun GhestaTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(primary = p.primary, onPrimary = p.onPrimary, background = p.bg, surface = p.surface, onSurface = p.ink)
    } else {
        lightColorScheme(primary = p.primary, onPrimary = p.onPrimary, background = p.bg, surface = p.surface, onSurface = p.ink)
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(
        LocalPalette provides p,
        LocalLayoutDirection provides LayoutDirection.Rtl,
    ) {
        MaterialTheme(colorScheme = scheme, typography = vazirTypography(), content = content)
    }
}
