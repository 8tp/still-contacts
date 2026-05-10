package dev.chuds.stillcontacts.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chuds.stillcontacts.ui.theme.LocalHaptics
import dev.chuds.stillcontacts.ui.theme.StillColors
import dev.chuds.stillcontacts.ui.theme.StillTypography

/**
 * Lowercase verb tap target. `bordered = true` is for persistent footer verbs only —
 * row taps, action sheets, the letter rail, and top headers stay borderless.
 * Plays a subtle haptic via [LocalHaptics] before invoking [onClick].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StillVerb(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bordered: Boolean = false,
    color: Color = StillColors.MutedWhite,
    style: TextStyle = StillTypography.Menu,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHaptics.current
    Text(
        text = text,
        style = style,
        color = color,
        modifier = modifier
            .then(
                if (bordered) Modifier.border(1.dp, StillColors.Hairline, RectangleShape)
                else Modifier,
            )
            .combinedClickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = {
                    haptics()
                    onClick()
                },
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
