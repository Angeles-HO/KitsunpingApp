package app.kitsunping.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED,
    TERMINAL,
    CRIMSON,
    SOLARIZED,
    ARCTIC,
    ROSE,
    MODERN,
    MODERN_INVERTED,
    FOREST,
    OCEAN,
    SUNSET,
    MONOCHROME,
    PASTEL,
    DRACULA,
    NORD,
    MONO_BLUEPRINT,
    KITSUNPING
}

private fun lightAppScheme(
    background: Color,
    surface: Color,
    surfaceAlt: Color,
    border: Color,
    primary: Color,
    primaryStrong: Color,
    success: Color,
    successContainer: Color,
    error: Color,
    text: Color,
    textMuted: Color,
    accent: Color = primaryStrong,
    onPrimary: Color = ColorWhite,
    onSecondary: Color = ColorWhite,
    onTertiary: Color = ColorWhite,
    onError: Color = ColorWhite
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryStrong.copy(alpha = 0.16f),
    onPrimaryContainer = text,
    secondary = accent,
    onSecondary = onSecondary,
    secondaryContainer = accent.copy(alpha = 0.14f),
    onSecondaryContainer = text,
    tertiary = success,
    onTertiary = onTertiary,
    tertiaryContainer = successContainer,
    onTertiaryContainer = text,
    error = error,
    onError = onError,
    errorContainer = error.copy(alpha = 0.14f),
    onErrorContainer = text,
    background = background,
    onBackground = text,
    surface = surface,
    onSurface = text,
    surfaceVariant = surfaceAlt,
    onSurfaceVariant = textMuted,
    outline = border,
    outlineVariant = border.copy(alpha = 0.75f),
    surfaceTint = accent
)

private fun darkAppScheme(
    background: Color,
    surface: Color,
    surfaceAlt: Color,
    border: Color,
    primary: Color,
    primaryStrong: Color,
    success: Color,
    successContainer: Color,
    error: Color,
    text: Color,
    textMuted: Color,
    accent: Color = primaryStrong,
    onPrimary: Color = ColorWhite,
    onSecondary: Color = onPrimary,
    onTertiary: Color = ColorBlack,
    onError: Color = ColorBlack
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryStrong.copy(alpha = 0.16f),
    onPrimaryContainer = text,
    secondary = accent,
    onSecondary = onSecondary,
    secondaryContainer = surfaceAlt,
    onSecondaryContainer = text,
    tertiary = success,
    onTertiary = onTertiary,
    tertiaryContainer = successContainer,
    onTertiaryContainer = text,
    error = error,
    onError = onError,
    errorContainer = error.copy(alpha = 0.12f),
    onErrorContainer = text,
    background = background,
    onBackground = text,
    surface = surface,
    onSurface = text,
    surfaceVariant = surfaceAlt,
    onSurfaceVariant = textMuted,
    outline = border,
    outlineVariant = border.copy(alpha = 0.72f),
    surfaceTint = accent
)

private val LightColors = lightAppScheme(
    background = OpenWrtBackground,
    surface = OpenWrtSurface,
    surfaceAlt = OpenWrtSurfaceAlt,
    border = OpenWrtBorder,
    primary = OpenWrtPrimary,
    primaryStrong = OpenWrtAccent,
    success = OpenWrtSuccess,
    successContainer = OpenWrtSuccess.copy(alpha = 0.16f),
    error = OpenWrtError,
    text = OpenWrtText,
    textMuted = OpenWrtTextMuted,
    accent = OpenWrtAccent
)

private val DarkColors = darkAppScheme(
    background = DeepSpaceBackground,
    surface = DeepSpaceSurface,
    surfaceAlt = DeepSpaceSurfaceAlt,
    border = DeepSpaceBorder,
    primary = DeepSpacePrimary,
    primaryStrong = DeepSpacePrimaryStrong,
    success = DeepSpaceSuccess,
    successContainer = DeepSpaceSuccessContainer,
    error = DeepSpaceError,
    text = DeepSpaceText,
    textMuted = DeepSpaceTextMuted
)

private val AmoledColors = darkAppScheme(
    background = CyberStealthBackground,
    surface = CyberStealthSurface,
    surfaceAlt = CyberStealthSurfaceAlt,
    border = CyberStealthBorder,
    primary = CyberStealthPrimary,
    primaryStrong = CyberStealthPrimaryStrong,
    success = CyberStealthSuccess,
    successContainer = CyberStealthSuccessContainer,
    error = CyberStealthError,
    text = CyberStealthText,
    textMuted = CyberStealthTextMuted
)

private val TerminalColors = darkAppScheme(
    background = TerminalBackground,
    surface = TerminalSurface,
    surfaceAlt = TerminalSurfaceAlt,
    border = TerminalBorder,
    primary = TerminalPrimary,
    primaryStrong = TerminalPrimaryStrong,
    success = TerminalSuccess,
    successContainer = TerminalSuccessContainer,
    error = TerminalError,
    text = TerminalText,
    textMuted = TerminalTextMuted,
    onPrimary = ColorBlack,
    onSecondary = ColorBlack,
    onTertiary = ColorBlack,
    onError = ColorWhite
)

private val CrimsonColors = darkAppScheme(
    background = CrimsonBackground,
    surface = CrimsonSurface,
    surfaceAlt = CrimsonSurfaceAlt,
    border = CrimsonBorder,
    primary = CrimsonPrimary,
    primaryStrong = CrimsonPrimaryStrong,
    success = CrimsonSuccess,
    successContainer = CrimsonSuccessContainer,
    error = CrimsonError,
    text = CrimsonText,
    textMuted = CrimsonTextMuted,
    onPrimary = ColorWhite,
    onSecondary = ColorWhite,
    onTertiary = ColorBlack,
    onError = ColorWhite
)

private val SolarizedColors = darkAppScheme(
    background = SolarizedBackground,
    surface = SolarizedSurface,
    surfaceAlt = SolarizedSurfaceAlt,
    border = SolarizedBorder,
    primary = SolarizedPrimary,
    primaryStrong = SolarizedPrimaryStrong,
    success = SolarizedSuccess,
    successContainer = SolarizedSuccessContainer,
    error = SolarizedError,
    text = SolarizedText,
    textMuted = SolarizedTextMuted,
    accent = SolarizedPrimaryStrong,
    onPrimary = ColorWhite,
    onSecondary = ColorBlack,
    onTertiary = ColorBlack,
    onError = ColorWhite
)

private val ArcticColors = lightAppScheme(
    background = ArcticBackground,
    surface = ArcticSurface,
    surfaceAlt = ArcticSurfaceAlt,
    border = ArcticBorder,
    primary = ArcticPrimary,
    primaryStrong = ArcticPrimaryStrong,
    success = ArcticSuccess,
    successContainer = ArcticSuccessContainer,
    error = ArcticError,
    text = ArcticText,
    textMuted = ArcticTextMuted,
    accent = ArcticPrimaryStrong
)

private val RoseColors = lightAppScheme(
    background = RoseBackground,
    surface = RoseSurface,
    surfaceAlt = RoseSurfaceAlt,
    border = RoseBorder,
    primary = RosePrimary,
    primaryStrong = RosePrimaryStrong,
    success = RoseSuccess,
    successContainer = RoseSuccessContainer,
    error = RoseError,
    text = RoseText,
    textMuted = RoseTextMuted,
    accent = RosePrimaryStrong
)

private val ModernColors = darkAppScheme(
    background = ModernBackground,
    surface = ModernSurface,
    surfaceAlt = ModernSurfaceAlt,
    border = ModernBorder,
    primary = ModernPrimary,
    primaryStrong = ModernPrimaryStrong,
    success = ModernSuccess,
    successContainer = ModernSuccessContainer,
    error = ModernError,
    text = ModernText,
    textMuted = ModernTextMuted,
    onPrimary = ColorBlack,
    onSecondary = ColorBlack,
    onTertiary = ColorBlack,
    onError = ColorWhite
)

private val ModernInvertedColors = lightAppScheme(
    background = ModernInvertedBackground,
    surface = ModernInvertedSurface,
    surfaceAlt = ModernInvertedSurfaceAlt,
    border = ModernInvertedBorder,
    primary = ModernInvertedPrimary,
    primaryStrong = ModernInvertedPrimaryStrong,
    success = ModernInvertedSuccess,
    successContainer = ModernInvertedSuccessContainer,
    error = ModernInvertedError,
    text = ModernInvertedText,
    textMuted = ModernInvertedTextMuted,
    accent = ModernInvertedPrimaryStrong,
    onPrimary = ColorWhite,
    onSecondary = ColorWhite,
    onTertiary = ColorBlack,
    onError = ColorWhite
)

private val ForestColors = darkAppScheme(
    background = ForestBackground,
    surface = ForestSurface,
    surfaceAlt = ForestSurfaceAlt,
    border = ForestBorder,
    primary = ForestPrimary,
    primaryStrong = ForestPrimaryStrong,
    success = ForestSuccess,
    successContainer = ForestSuccessContainer,
    error = ForestError,
    text = ForestText,
    textMuted = ForestTextMuted,
    onPrimary = ColorBlack,
    onSecondary = ColorBlack,
    onTertiary = ColorBlack,
    onError = ColorWhite
)

private val OceanColors = darkAppScheme(
    background = OceanBackground,
    surface = OceanSurface,
    surfaceAlt = OceanSurfaceAlt,
    border = OceanBorder,
    primary = OceanPrimary,
    primaryStrong = OceanPrimaryStrong,
    success = OceanSuccess,
    successContainer = OceanSuccessContainer,
    error = OceanError,
    text = OceanText,
    textMuted = OceanTextMuted,
    onPrimary = ColorWhite,
    onSecondary = ColorWhite,
    onTertiary = ColorBlack,
    onError = ColorWhite
)

private val SunsetColors = darkAppScheme(
    background = SunsetBackground,
    surface = SunsetSurface,
    surfaceAlt = SunsetSurfaceAlt,
    border = SunsetBorder,
    primary = SunsetPrimary,
    primaryStrong = SunsetPrimaryStrong,
    success = SunsetSuccess,
    successContainer = SunsetSuccessContainer,
    error = SunsetError,
    text = SunsetText,
    textMuted = SunsetTextMuted,
    onPrimary = ColorBlack,
    onSecondary = ColorBlack,
    onTertiary = ColorBlack,
    onError = ColorWhite
)

private val MonochromeColors = darkAppScheme(
    background = MonochromeBackground,
    surface = MonochromeSurface,
    surfaceAlt = MonochromeSurfaceAlt,
    border = MonochromeBorder,
    primary = MonochromePrimary,
    primaryStrong = MonochromePrimaryStrong,
    success = MonochromeSuccess,
    successContainer = MonochromeSuccessContainer,
    error = MonochromeError,
    text = MonochromeText,
    textMuted = MonochromeTextMuted,
    onPrimary = ColorBlack,
    onSecondary = ColorBlack,
    onTertiary = ColorBlack,
    onError = ColorWhite
)

private val PastelColors = lightAppScheme(
    background = PastelBackground,
    surface = PastelSurface,
    surfaceAlt = PastelSurfaceAlt,
    border = PastelBorder,
    primary = PastelPrimary,
    primaryStrong = PastelPrimaryStrong,
    success = PastelSuccess,
    successContainer = PastelSuccessContainer,
    error = PastelError,
    text = PastelText,
    textMuted = PastelTextMuted,
    accent = PastelPrimaryStrong,
    onPrimary = ColorWhite,
    onSecondary = ColorWhite,
    onTertiary = ColorBlack,
    onError = ColorWhite
)

private val DraculaColors = darkAppScheme(
    background = DraculaBackground,
    surface = DraculaSurface,
    surfaceAlt = DraculaSurfaceAlt,
    border = DraculaBorder,
    primary = DraculaPrimary,
    primaryStrong = DraculaPrimaryStrong,
    success = DraculaSuccess,
    successContainer = DraculaSuccessContainer,
    error = DraculaError,
    text = DraculaText,
    textMuted = DraculaTextMuted,
    onPrimary = ColorBlack,
    onSecondary = ColorBlack,
    onTertiary = ColorBlack,
    onError = ColorWhite
)

private val NordColors = darkAppScheme(
    background = NordBackground,
    surface = NordSurface,
    surfaceAlt = NordSurfaceAlt,
    border = NordBorder,
    primary = NordPrimary,
    primaryStrong = NordPrimaryStrong,
    success = NordSuccess,
    successContainer = NordSuccessContainer,
    error = NordError,
    text = NordText,
    textMuted = NordTextMuted,
    onPrimary = ColorBlack,
    onSecondary = ColorWhite,
    onTertiary = ColorBlack,
    onError = ColorWhite
)

private val MonoBlueprintColors = darkAppScheme(
    background = MonoBlueprintBackground,
    surface = MonoBlueprintSurface,
    surfaceAlt = MonoBlueprintSurfaceAlt,
    border = MonoBlueprintBorder,
    primary = MonoBlueprintPrimary,
    primaryStrong = MonoBlueprintPrimaryStrong,
    success = MonoBlueprintSuccess,
    successContainer = MonoBlueprintSuccessContainer,
    error = MonoBlueprintError,
    text = MonoBlueprintText,
    textMuted = MonoBlueprintTextMuted,
    onPrimary = ColorBlack,
    onSecondary = ColorBlack,
    onTertiary = ColorBlack,
    onError = ColorBlack
)

private val KitsunpingColors = darkAppScheme(
    background = KitsunpingBackground,
    surface = KitsunpingSurface,
    surfaceAlt = KitsunpingSurfaceAlt,
    border = KitsunpingBorder,
    primary = KitsunpingPrimary,
    primaryStrong = KitsunpingPrimaryStrong,
    success = KitsunpingSuccess,
    successContainer = KitsunpingSuccess.copy(alpha = 0.18f),
    error = KitsunpingError,
    text = KitsunpingText,
    textMuted = KitsunpingTextMuted,
    accent = KitsunpingWarning,
    onPrimary = ColorWhite,
    onSecondary = ColorBlack,
    onTertiary = ColorBlack,
    onError = ColorWhite
)

@Composable
fun KitsunpingTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val colors = when (themeMode) {
        AppThemeMode.SYSTEM -> if (isSystemDark) DarkColors else LightColors
        AppThemeMode.LIGHT -> LightColors
        AppThemeMode.DARK -> DarkColors
        AppThemeMode.AMOLED -> AmoledColors
        AppThemeMode.TERMINAL -> TerminalColors
        AppThemeMode.CRIMSON -> CrimsonColors
        AppThemeMode.SOLARIZED -> SolarizedColors
        AppThemeMode.ARCTIC -> ArcticColors
        AppThemeMode.ROSE -> RoseColors
        AppThemeMode.MODERN -> ModernColors
        AppThemeMode.MODERN_INVERTED -> ModernInvertedColors
        AppThemeMode.FOREST -> ForestColors
        AppThemeMode.OCEAN -> OceanColors
        AppThemeMode.SUNSET -> SunsetColors
        AppThemeMode.MONOCHROME -> MonochromeColors
        AppThemeMode.PASTEL -> PastelColors
        AppThemeMode.DRACULA -> DraculaColors
        AppThemeMode.NORD -> NordColors
        AppThemeMode.MONO_BLUEPRINT -> MonoBlueprintColors
        AppThemeMode.KITSUNPING -> KitsunpingColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
