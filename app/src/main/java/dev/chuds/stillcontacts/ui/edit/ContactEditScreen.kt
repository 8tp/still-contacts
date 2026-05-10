package dev.chuds.stillcontacts.ui.edit

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import dev.chuds.stillcontacts.data.Contact
import dev.chuds.stillcontacts.data.ContactDetail
import dev.chuds.stillcontacts.data.TypeLabel
import dev.chuds.stillcontacts.data.TypedValue
import dev.chuds.stillcontacts.ui.theme.StillColors
import dev.chuds.stillcontacts.ui.theme.StillTypography
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactEditScreen(
    initial: ContactDetail?,
    onSave: (ContactDetail) -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    var given by remember { mutableStateOf(initial?.contact?.givenName.orEmpty()) }
    var family by remember { mutableStateOf(initial?.contact?.familyName.orEmpty()) }
    val phones = remember { (initial?.phones ?: emptyList()).toMutableStateList() }
    val emails = remember { (initial?.emails ?: emptyList()).toMutableStateList() }
    val addresses = remember { (initial?.addresses ?: emptyList()).toMutableStateList() }
    var organization by remember { mutableStateOf(initial?.organization.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var birthdayText by remember {
        mutableStateOf(initial?.birthday?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")).orEmpty())
    }

    val parsedBirthday: LocalDate? = remember(birthdayText) {
        if (birthdayText.isBlank()) null
        else runCatching { LocalDate.parse(birthdayText, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }.getOrNull()
    }
    val birthdayValid = birthdayText.isBlank() || parsedBirthday != null

    val hasName = given.isNotBlank() || family.isNotBlank()
    val hasPhone = phones.any { it.value.isNotBlank() }
    val hasEmail = emails.any { it.value.isNotBlank() }
    val saveEnabled = (hasName || hasPhone || hasEmail) && birthdayValid

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
                .imePadding()
                .padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 96.dp),
        ) {
            Text(
                text = if (initial == null) "NEW CONTACT" else "EDIT",
                style = StillTypography.Kicker,
                color = StillColors.MutedWhite,
                modifier = Modifier.padding(bottom = 18.dp),
            )

            // Name
            NameField(value = given, placeholder = "given", onChange = { given = it })
            Spacer(Modifier.height(8.dp))
            NameField(value = family, placeholder = "family", onChange = { family = it })

            Spacer(Modifier.height(28.dp))
            TypedSection(
                label = "phones",
                values = phones,
                addVerb = "add phone",
            )

            Spacer(Modifier.height(20.dp))
            TypedSection(
                label = "emails",
                values = emails,
                addVerb = "add email",
            )

            Spacer(Modifier.height(20.dp))
            TypedSection(
                label = "addresses",
                values = addresses,
                addVerb = "add address",
                multiline = true,
            )

            Spacer(Modifier.height(24.dp))
            Text(
                text = "organization",
                style = StillTypography.Caption,
                color = StillColors.Gray,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            FlatField(value = organization, onChange = { organization = it }, placeholder = "company")

            Spacer(Modifier.height(20.dp))
            Text(
                text = "birthday",
                style = StillTypography.Caption,
                color = StillColors.Gray,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            FlatField(value = birthdayText, onChange = { birthdayText = it }, placeholder = "yyyy-mm-dd")

            Spacer(Modifier.height(20.dp))
            Text(
                text = "notes",
                style = StillTypography.Caption,
                color = StillColors.Gray,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            FlatField(value = notes, onChange = { notes = it }, placeholder = "—", multiline = true)

            Spacer(Modifier.height(28.dp))
            if (!saveEnabled) {
                Text(
                    text = if (!birthdayValid) "birthday must be yyyy-mm-dd"
                    else "needs name, phone, or email",
                    style = StillTypography.Caption,
                    color = StillColors.DimGray,
                )
            }
        }

        FooterBar(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding(),
            saveEnabled = saveEnabled,
            onSave = {
                val cleanedPhones = phones.filter { it.value.isNotBlank() }
                val cleanedEmails = emails.filter { it.value.isNotBlank() }
                val cleanedAddresses = addresses.filter { it.value.isNotBlank() }
                val displayName = listOf(given, family).filter { it.isNotBlank() }.joinToString(" ")
                val baseContact = initial?.contact ?: Contact(
                    rawContactId = -1L,
                    lookupKey = "",
                    displayName = displayName,
                    familyName = family.takeIf { it.isNotBlank() },
                    givenName = given.takeIf { it.isNotBlank() },
                    primaryPhone = cleanedPhones.firstOrNull()?.value,
                    primaryEmail = cleanedEmails.firstOrNull()?.value,
                    sectionLetter = displayName.trimStart().firstOrNull()
                        ?.let { if (it.isLetter()) it.uppercaseChar() else '#' } ?: '#',
                )
                onSave(
                    ContactDetail(
                        contact = baseContact.copy(
                            displayName = displayName,
                            givenName = given.takeIf { it.isNotBlank() },
                            familyName = family.takeIf { it.isNotBlank() },
                            primaryPhone = cleanedPhones.firstOrNull()?.value,
                            primaryEmail = cleanedEmails.firstOrNull()?.value,
                        ),
                        phones = cleanedPhones,
                        emails = cleanedEmails,
                        addresses = cleanedAddresses,
                        organization = organization.takeIf { it.isNotBlank() },
                        notes = notes.takeIf { it.isNotBlank() },
                        birthday = parsedBirthday,
                    ),
                )
            },
            onDelete = onDelete,
            onCancel = onCancel,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NameField(value: String, placeholder: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = StillTypography.Title.copy(color = StillColors.SoftWhite),
        cursorBrush = SolidColor(StillColors.SoftWhite),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = StillTypography.Title,
                    color = StillColors.DimGray,
                )
            }
            inner()
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FlatField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    multiline: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = !multiline,
        textStyle = StillTypography.Body.copy(color = StillColors.SoftWhite),
        cursorBrush = SolidColor(StillColors.SoftWhite),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = StillTypography.Body,
                    color = StillColors.DimGray,
                )
            }
            inner()
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TypedSection(
    label: String,
    values: SnapshotStateList<TypedValue>,
    addVerb: String,
    multiline: Boolean = false,
) {
    Text(
        text = label,
        style = StillTypography.Caption,
        color = StillColors.Gray,
        modifier = Modifier.padding(bottom = 6.dp),
    )
    values.forEachIndexed { idx, tv ->
        val typeInteraction = remember(idx) { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tv.type.label,
                style = StillTypography.Caption,
                color = StillColors.Gray,
                modifier = Modifier
                    .width(64.dp)
                    .combinedClickable(
                        interactionSource = typeInteraction,
                        indication = null,
                        onClick = {
                            values[idx] = tv.copy(type = tv.type.next())
                        },
                    )
                    .padding(vertical = 4.dp),
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = tv.value,
                onValueChange = { newValue -> values[idx] = tv.copy(value = newValue) },
                singleLine = !multiline,
                textStyle = StillTypography.Body.copy(color = StillColors.SoftWhite),
                cursorBrush = SolidColor(StillColors.SoftWhite),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (tv.value.isEmpty()) {
                        Text(
                            text = "—",
                            style = StillTypography.Body,
                            color = StillColors.DimGray,
                        )
                    }
                    inner()
                },
            )
            Spacer(Modifier.width(8.dp))
            val removeInteraction = remember(idx) { MutableInteractionSource() }
            Text(
                text = "remove",
                style = StillTypography.Caption,
                color = StillColors.DimGray,
                modifier = Modifier.combinedClickable(
                    interactionSource = removeInteraction,
                    indication = null,
                    onClick = { values.removeAt(idx) },
                ),
            )
        }
    }
    val addInteraction = remember { MutableInteractionSource() }
    Text(
        text = addVerb,
        style = StillTypography.Caption,
        color = StillColors.MutedWhite,
        modifier = Modifier
            .combinedClickable(
                interactionSource = addInteraction,
                indication = null,
                onClick = { values.add(TypedValue(TypeLabel.Mobile, "")) },
            )
            .padding(top = 6.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FooterBar(
    modifier: Modifier = Modifier,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    val saveInteraction = remember { MutableInteractionSource() }
    val deleteInteraction = remember { MutableInteractionSource() }
    val cancelInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "cancel",
            style = StillTypography.Menu,
            color = StillColors.MutedWhite,
            modifier = Modifier.combinedClickable(
                interactionSource = cancelInteraction,
                indication = null,
                onClick = onCancel,
            ),
        )
        if (onDelete != null) {
            Text(
                text = "delete",
                style = StillTypography.Menu,
                color = StillColors.DimGray,
                modifier = Modifier.combinedClickable(
                    interactionSource = deleteInteraction,
                    indication = null,
                    onClick = onDelete,
                ),
            )
        }
        Text(
            text = "save",
            style = StillTypography.Menu,
            color = if (saveEnabled) StillColors.SoftWhite else StillColors.DimGray,
            modifier = Modifier.combinedClickable(
                enabled = saveEnabled,
                interactionSource = saveInteraction,
                indication = null,
                onClick = onSave,
            ),
        )
    }
}
