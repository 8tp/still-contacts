package dev.chuds.stillcontacts.ui.components

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.chuds.stillcontacts.ui.theme.StillColors
import dev.chuds.stillcontacts.ui.theme.StillTypography

/**
 * The vertical letter rail at the right edge of the contacts list. Renders a-z then '#'.
 * Drag — continuous scrub by mapping pointer Y to the corresponding letter index.
 * Tap — snap to that letter's section.
 *
 * The rail is the only piece of custom drawing in v0.1; everything else is text-first.
 */
@Composable
fun StillLetterRail(
    activeLetter: Char,
    onSelect: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val letters = remember { ('A'..'Z').toList() + '#' }

    var size by remember { mutableStateOf(Size.Zero) }

    fun letterAt(yPx: Float): Char? {
        val rowHeight = size.height / letters.size
        if (rowHeight <= 0f) return null
        val idx = (yPx / rowHeight).toInt().coerceIn(0, letters.lastIndex)
        return letters[idx]
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(28.dp)
            .pointerInput(Unit) {
                size = Size(this.size.width.toFloat(), this.size.height.toFloat())
                detectTapGestures(
                    onTap = { offset ->
                        size = Size(this.size.width.toFloat(), this.size.height.toFloat())
                        letterAt(offset.y)?.let(onSelect)
                    },
                )
            }
            .pointerInput(Unit) {
                size = Size(this.size.width.toFloat(), this.size.height.toFloat())
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        size = Size(this.size.width.toFloat(), this.size.height.toFloat())
                        letterAt(offset.y)?.let(onSelect)
                    },
                    onVerticalDrag = { change, _ ->
                        letterAt(change.position.y)?.let(onSelect)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { ch ->
                val active = ch == activeLetter
                Text(
                    text = ch.toString().lowercase(),
                    style = StillTypography.Kicker,
                    color = if (active) StillColors.SoftWhite else StillColors.DimGray,
                )
            }
        }
    }
}
