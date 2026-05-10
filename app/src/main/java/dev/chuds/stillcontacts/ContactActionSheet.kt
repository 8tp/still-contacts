package dev.chuds.stillcontacts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chuds.stillcontacts.ui.theme.StillColors
import dev.chuds.stillcontacts.ui.theme.StillTypography

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContactActionSheet(
    displayName: String,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dismissInteraction = remember { MutableInteractionSource() }
    val openInteraction = remember { MutableInteractionSource() }
    val editInteraction = remember { MutableInteractionSource() }
    val exportInteraction = remember { MutableInteractionSource() }
    val deleteInteraction = remember { MutableInteractionSource() }
    val cancelInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StillColors.OledBlack.copy(alpha = 0.94f))
            .combinedClickable(
                interactionSource = dismissInteraction,
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Text(
                text = displayName.ifBlank { "—" },
                style = StillTypography.Caption,
                color = StillColors.DimGray,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            ActionRow("open", onOpen, openInteraction)
            ActionRow("edit", onEdit, editInteraction)
            ActionRow("export", onExport, exportInteraction)
            ActionRow("delete", onDelete, deleteInteraction)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "cancel",
                style = StillTypography.Menu,
                color = StillColors.Gray,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = cancelInteraction,
                        indication = null,
                        onClick = onDismiss,
                    )
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionRow(
    label: String,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
) {
    Text(
        text = label,
        style = StillTypography.Menu,
        color = StillColors.SoftWhite,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
    )
}
