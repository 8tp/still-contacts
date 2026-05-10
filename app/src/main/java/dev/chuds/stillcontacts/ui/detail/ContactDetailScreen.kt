package dev.chuds.stillcontacts.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.chuds.stillcontacts.data.ContactDetail
import dev.chuds.stillcontacts.data.TypedValue
import dev.chuds.stillcontacts.ui.theme.StillColors
import dev.chuds.stillcontacts.ui.theme.StillTypography
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactDetailScreen(
    detail: ContactDetail,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StillColors.OledBlack),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 96.dp),
        ) {
            Text(
                text = detail.contact.displayName.ifBlank { "—" },
                style = StillTypography.Display,
                color = StillColors.SoftWhite,
            )
            if (!detail.organization.isNullOrBlank()) {
                Text(
                    text = detail.organization,
                    style = StillTypography.Caption,
                    color = StillColors.Gray,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(28.dp))

            Section(label = "phones", values = detail.phones) { tv ->
                val uri = Uri.parse("tel:${tv.value.trim()}")
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_DIAL, uri))
                }
            }
            Section(label = "emails", values = detail.emails) { tv ->
                val uri = Uri.parse("mailto:${tv.value.trim()}")
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, uri))
                }
            }
            Section(label = "addresses", values = detail.addresses) { tv ->
                val q = Uri.encode(tv.value)
                val uri = Uri.parse("geo:0,0?q=$q")
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            }
            detail.birthday?.let { date ->
                MetaRow(
                    label = "birthday",
                    value = date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                )
            }
            if (!detail.notes.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "notes",
                    style = StillTypography.Caption,
                    color = StillColors.Gray,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = detail.notes,
                    style = StillTypography.Body,
                    color = StillColors.SoftWhite,
                )
            }
        }

        FooterBar(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .navigationBarsPadding(),
            onBack = onBack,
            onEdit = onEdit,
            onExport = onExport,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Section(
    label: String,
    values: List<TypedValue>,
    onTap: (TypedValue) -> Unit,
) {
    if (values.isEmpty()) return
    Spacer(Modifier.height(16.dp))
    Text(
        text = label,
        style = StillTypography.Caption,
        color = StillColors.Gray,
        modifier = Modifier.padding(bottom = 6.dp),
    )
    values.forEach { tv ->
        val interaction = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { onTap(tv) },
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tv.type.label,
                style = StillTypography.Caption,
                color = StillColors.Gray,
                modifier = Modifier.width(64.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = tv.value,
                style = StillTypography.Body,
                color = StillColors.SoftWhite,
            )
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = StillTypography.Caption,
            color = StillColors.Gray,
            modifier = Modifier.width(80.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = StillTypography.Body,
            color = StillColors.SoftWhite,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FooterBar(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
) {
    val backInteraction = remember { MutableInteractionSource() }
    val editInteraction = remember { MutableInteractionSource() }
    val exportInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "back",
            style = StillTypography.Menu,
            color = StillColors.MutedWhite,
            modifier = Modifier.combinedClickable(
                interactionSource = backInteraction,
                indication = null,
                onClick = onBack,
            ),
        )
        Text(
            text = "edit",
            style = StillTypography.Menu,
            color = StillColors.SoftWhite,
            modifier = Modifier.combinedClickable(
                interactionSource = editInteraction,
                indication = null,
                onClick = onEdit,
            ),
        )
        Text(
            text = "export",
            style = StillTypography.Menu,
            color = StillColors.MutedWhite,
            modifier = Modifier.combinedClickable(
                interactionSource = exportInteraction,
                indication = null,
                onClick = onExport,
            ),
        )
    }
}
