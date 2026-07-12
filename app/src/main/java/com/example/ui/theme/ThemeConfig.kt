package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class AppTheme(val displayName: String) {
    IMMERSIVE_UI("Immersive UI"),
    COSMIC_SLATE("Cosmic Slate"),
    CYBERPUNK("Cyberpunk Gold"),
    SAKURA_TWILIGHT("Sakura Twilight"),
    OBSIDIAN_ABYSS("Obsidian Abyss"),
    WARM_SEPIA("Warm Sepia"),
    PRISTINE_PAPER("Pristine Paper (Light)"),
    CLASSIC_LIGHT("Classic Light")
}

object ThemeConfig {

    // 0. Immersive UI (Aesthetic dark layout)
    val ImmersiveUiScheme = darkColorScheme(
        primary = Color(0xFFD0E4FF),
        onPrimary = Color(0xFF003258),
        primaryContainer = Color(0xFF00497E),
        onPrimaryContainer = Color(0xFFD1E4FF),
        secondary = Color(0xFF2D2F31),
        onSecondary = Color(0xFFE2E2E6),
        background = Color(0xFF0E1113),
        onBackground = Color(0xFFE2E2E6),
        surface = Color(0xFF16191B),
        onSurface = Color(0xFFE2E2E6),
        surfaceVariant = Color(0xFF1B2024),
        onSurfaceVariant = Color(0xFF909194),
        outline = Color(0xFF2D2F31)
    )

    // 1. Cosmic Slate (Teal / Slate)
    val CosmicSlateScheme = darkColorScheme(
        primary = Color(0xFF1FA588),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF188C73),
        onPrimaryContainer = Color(0xFFE8ECEC),
        secondary = Color(0xFF283135),
        onSecondary = Color(0xFFE8ECEC),
        background = Color(0xFF101415),
        onBackground = Color(0xFFE8ECEC),
        surface = Color(0xFF161B1D),
        onSurface = Color(0xFFE8ECEC),
        surfaceVariant = Color(0xFF1A2023),
        onSurfaceVariant = Color(0xFF8FA0A0),
        outline = Color(0xFF283135)
    )

    // 2. Cyberpunk Gold (Amber / Neon Dark)
    val CyberpunkScheme = darkColorScheme(
        primary = Color(0xFFFFB300),
        onPrimary = Color.Black,
        primaryContainer = Color(0xFFB37D00),
        onPrimaryContainer = Color.White,
        secondary = Color(0xFF1A1A1A),
        onSecondary = Color(0xFFFFB300),
        background = Color(0xFF0A0A0A),
        onBackground = Color(0xFFE0E0E0),
        surface = Color(0xFF121212),
        onSurface = Color(0xFFFFB300),
        surfaceVariant = Color(0xFF1E1E1E),
        onSurfaceVariant = Color(0xFFA0A0A0),
        outline = Color(0xFF2D2D2D)
    )

    // 3. Sakura Twilight (Midnight Purple / Cherry Blossom)
    val SakuraTwilightScheme = darkColorScheme(
        primary = Color(0xFFF48FB1),
        onPrimary = Color(0xFF4A148C),
        primaryContainer = Color(0xFFAD1457),
        onPrimaryContainer = Color(0xFFFCE4EC),
        secondary = Color(0xFF2D1B2D),
        onSecondary = Color(0xFFF48FB1),
        background = Color(0xFF120B12),
        onBackground = Color(0xFFECE0EC),
        surface = Color(0xFF1A111A),
        onSurface = Color(0xFFECE0EC),
        surfaceVariant = Color(0xFF221622),
        onSurfaceVariant = Color(0xFFB59BB5),
        outline = Color(0xFF322132)
    )

    // 4. Obsidian Abyss (Minimalist Pure Black / Ice Blue)
    val ObsidianAbyssScheme = darkColorScheme(
        primary = Color(0xFF80D8FF),
        onPrimary = Color.Black,
        primaryContainer = Color(0xFF00B0FF),
        onPrimaryContainer = Color.White,
        secondary = Color(0xFF151515),
        onSecondary = Color(0xFF80D8FF),
        background = Color(0xFF000000),
        onBackground = Color(0xFFF5F5F5),
        surface = Color(0xFF0D0D0D),
        onSurface = Color(0xFFF5F5F5),
        surfaceVariant = Color(0xFF1A1A1A),
        onSurfaceVariant = Color(0xFF9E9E9E),
        outline = Color(0xFF222222)
    )

    // 5. Warm Sepia (Book Comfort Theme)
    val WarmSepiaScheme = darkColorScheme(
        primary = Color(0xFFE5A65D),
        onPrimary = Color(0xFF2B1D12),
        primaryContainer = Color(0xFF8C5C2E),
        onPrimaryContainer = Color(0xFFFFF2E6),
        secondary = Color(0xFF2D231E),
        onSecondary = Color(0xFFE5A65D),
        background = Color(0xFF16110F),
        onBackground = Color(0xFFE6D6CD),
        surface = Color(0xFF1F1816),
        onSurface = Color(0xFFE6D6CD),
        surfaceVariant = Color(0xFF29201D),
        onSurfaceVariant = Color(0xFFBCAAA4),
        outline = Color(0xFF3E302B)
    )

    // 6. Pristine Paper (Comfortable pure white / soft cream light theme)
    val PristinePaperScheme = lightColorScheme(
        primary = Color(0xFF1E1E1E),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF0F0F0),
        onPrimaryContainer = Color(0xFF1E1E1E),
        secondary = Color(0xFFECECEC),
        onSecondary = Color(0xFF1E1E1E),
        background = Color(0xFFFCFBF9), // warm off-white book paper color
        onBackground = Color(0xFF111111),
        surface = Color(0xFFF5F4F0),
        onSurface = Color(0xFF111111),
        surfaceVariant = Color(0xFFECEBE6),
        onSurfaceVariant = Color(0xFF555555),
        outline = Color(0xFFCCCCCC)
    )

    // 7. Classic Light (Vibrant modern light style)
    val ClassicLightScheme = lightColorScheme(
        primary = Color(0xFF005FAF),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD1E4FF),
        onPrimaryContainer = Color(0xFF001D36),
        secondary = Color(0xFFF0F4FA),
        onSecondary = Color(0xFF005FAF),
        background = Color(0xFFFAFAFC),
        onBackground = Color(0xFF1C1B1F),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1C1B1F),
        surfaceVariant = Color(0xFFF1F1F5),
        onSurfaceVariant = Color(0xFF49454F),
        outline = Color(0xFF79747E)
    )

    fun getColorScheme(theme: AppTheme): ColorScheme {
        return when (theme) {
            AppTheme.IMMERSIVE_UI -> ImmersiveUiScheme
            AppTheme.COSMIC_SLATE -> CosmicSlateScheme
            AppTheme.CYBERPUNK -> CyberpunkScheme
            AppTheme.SAKURA_TWILIGHT -> SakuraTwilightScheme
            AppTheme.OBSIDIAN_ABYSS -> ObsidianAbyssScheme
            AppTheme.WARM_SEPIA -> WarmSepiaScheme
            AppTheme.PRISTINE_PAPER -> PristinePaperScheme
            AppTheme.CLASSIC_LIGHT -> ClassicLightScheme
        }
    }
}
