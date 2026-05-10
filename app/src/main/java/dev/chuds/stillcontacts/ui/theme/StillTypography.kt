package dev.chuds.stillcontacts.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.chuds.stillcontacts.data.FontPreset

/**
 * Concrete typography values for the active font preset. Read via [StillTypography] inside
 * a Composable; provide via [LocalStillTypography] at the composition root.
 *
 * Roles tuned for a contacts surface:
 *   Kicker   — uppercase mono labels, including the section letters in the right rail (a, b, c…)
 *   Display  — the contact name on the detail screen, large
 *   Title    — contact display name on a list row
 *   Menu     — settings rows, lowercase verbs in footers
 *   Caption  — type labels (`mobile`, `home`, `work`), section headers in the list
 *   Small    — phone numbers, emails on list rows; secondary metadata
 *   Body     — edit-field text and freeform notes
 */
data class StillTypographyValues(
    val Kicker: TextStyle,
    val Display: TextStyle,
    val Title: TextStyle,
    val Menu: TextStyle,
    val Caption: TextStyle,
    val Small: TextStyle,
    val Body: TextStyle,
)

fun stillTypographyValues(
    serifFont: FontFamily,
    sansFont: FontFamily,
    monoFont: FontFamily,
): StillTypographyValues = StillTypographyValues(
    Kicker = TextStyle(
        fontFamily = monoFont,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.8.sp,
        fontWeight = FontWeight.Normal,
    ),
    Display = TextStyle(
        fontFamily = serifFont,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.6).sp,
        fontWeight = FontWeight.Light,
    ),
    Title = TextStyle(
        fontFamily = serifFont,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
        fontWeight = FontWeight.Light,
    ),
    Menu = TextStyle(
        fontFamily = sansFont,
        fontSize = 22.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.2.sp,
        fontWeight = FontWeight.Light,
    ),
    Caption = TextStyle(
        fontFamily = monoFont,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.7.sp,
        fontWeight = FontWeight.Normal,
    ),
    Small = TextStyle(
        fontFamily = sansFont,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.2.sp,
        fontWeight = FontWeight.Light,
    ),
    Body = TextStyle(
        fontFamily = sansFont,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.2.sp,
        fontWeight = FontWeight.Normal,
    ),
)

fun stillTypographyFor(preset: FontPreset): StillTypographyValues = when (preset) {
    FontPreset.System -> stillTypographyValues(
        serifFont = FontFamily.Serif,
        sansFont = FontFamily.SansSerif,
        monoFont = FontFamily.Monospace,
    )
    FontPreset.Editorial -> stillTypographyValues(
        serifFont = StillFontFamilies.CormorantGaramond,
        sansFont = StillFontFamilies.Inter,
        monoFont = StillFontFamilies.IbmPlexMono,
    )
    FontPreset.Terminal -> stillTypographyValues(
        serifFont = StillFontFamilies.IbmPlexMono,
        sansFont = StillFontFamilies.IbmPlexMono,
        monoFont = StillFontFamilies.IbmPlexMono,
    )
    FontPreset.Grotesk -> stillTypographyValues(
        serifFont = StillFontFamilies.InstrumentSerif,
        sansFont = StillFontFamilies.SpaceGrotesk,
        monoFont = StillFontFamilies.IbmPlexMono,
    )
}

val LocalStillTypography = staticCompositionLocalOf {
    stillTypographyFor(FontPreset.System)
}

object StillTypography {
    val Kicker: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalStillTypography.current.Kicker

    val Display: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalStillTypography.current.Display

    val Title: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalStillTypography.current.Title

    val Menu: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalStillTypography.current.Menu

    val Caption: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalStillTypography.current.Caption

    val Small: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalStillTypography.current.Small

    val Body: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalStillTypography.current.Body
}
