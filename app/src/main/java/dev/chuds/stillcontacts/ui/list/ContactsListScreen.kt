package dev.chuds.stillcontacts.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.chuds.stillcontacts.data.Contact
import dev.chuds.stillcontacts.ui.components.StillDivider
import dev.chuds.stillcontacts.ui.components.StillLetterRail
import dev.chuds.stillcontacts.ui.components.StillVerb
import dev.chuds.stillcontacts.ui.theme.StillColors
import dev.chuds.stillcontacts.ui.theme.StillTypography
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsListScreen(
    contacts: List<Contact>,
    permissionGranted: Boolean,
    pickMode: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenContact: (Contact) -> Unit,
    onLongPressContact: (Contact) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    onRequestRead: () -> Unit,
) {
    var searching by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val sections = remember(contacts) { contacts.groupBy { it.sectionLetter } }
    val sectionOrder = remember(sections) { sections.keys.sortedWith(SectionLetterComparator) }

    // Map each section letter to the absolute LazyColumn index of its first row.
    val sectionStartIndex = remember(contacts, sectionOrder) {
        var pos = 0
        val out = mutableMapOf<Char, Int>()
        for (letter in sectionOrder) {
            out[letter] = pos
            pos += 1 // section header
            pos += sections[letter]!!.size
        }
        out
    }

    val activeLetter by remember {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            sectionOrder.lastOrNull { (sectionStartIndex[it] ?: 0) <= firstVisible } ?: '#'
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StillColors.OledBlack),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            ListHeader(
                count = contacts.size,
                searching = searching,
                pickMode = pickMode,
                query = query,
                onQueryChange = onQueryChange,
            )

            when {
                !permissionGranted -> PermissionPrompt(onRequestRead)
                contacts.isEmpty() -> EmptyState(searching = searching || query.isNotBlank())
                else -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 24.dp, end = 36.dp, top = 4.dp, bottom = 96.dp),
                        ) {
                            sectionOrder.forEach { letter ->
                                item(key = "header-$letter") {
                                    SectionHeader(letter = letter)
                                }
                                val rows = sections[letter] ?: emptyList()
                                rows.forEach { c ->
                                    item(key = "row-${c.lookupKey}-${c.rawContactId}") {
                                        ContactRow(
                                            contact = c,
                                            onClick = { onOpenContact(c) },
                                            onLongClick = { onLongPressContact(c) },
                                        )
                                        StillDivider()
                                    }
                                }
                            }
                        }
                        if (!searching && query.isBlank()) {
                            StillLetterRail(
                                activeLetter = activeLetter,
                                onSelect = { letter ->
                                    val target = sectionStartIndex[letter]
                                        ?: sectionStartIndex[sectionOrder.firstOrNull { it >= letter } ?: return@StillLetterRail]
                                    if (target != null) {
                                        scope.launch {
                                            listState.scrollToItem(target)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        FooterBar(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding(),
            searching = searching,
            onSearchToggle = {
                searching = !searching
                if (!searching) onQueryChange("")
            },
            onNew = onNew,
            onSettings = onSettings,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListHeader(
    count: Int,
    searching: Boolean,
    pickMode: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 12.dp),
    ) {
        Text(
            text = if (pickMode) "PICK A CONTACT" else "CONTACTS",
            style = StillTypography.Kicker,
            color = StillColors.MutedWhite,
        )
        Text(
            text = when (count) {
                0 -> "no contacts yet — tap new"
                1 -> "1 contact"
                else -> "$count contacts"
            },
            style = StillTypography.Caption,
            color = StillColors.DimGray,
            modifier = Modifier.padding(top = 6.dp),
        )

        if (searching) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = StillTypography.Body.copy(color = StillColors.SoftWhite),
                cursorBrush = SolidColor(StillColors.SoftWhite),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .focusRequester(focusRequester),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = "search…",
                                style = StillTypography.Body,
                                color = StillColors.DimGray,
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}

@Composable
private fun SectionHeader(letter: Char) {
    Text(
        text = letter.toString().lowercase(),
        style = StillTypography.Caption,
        color = StillColors.MutedWhite,
        modifier = Modifier
            .fillMaxWidth()
            .background(StillColors.OledBlack)
            .padding(vertical = 8.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(
    contact: Contact,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = 14.dp),
    ) {
        Text(
            text = contact.displayName.ifBlank { "—" },
            style = StillTypography.Title,
            color = StillColors.SoftWhite,
        )
        val secondary = contact.primaryPhone ?: contact.primaryEmail
        if (!secondary.isNullOrBlank()) {
            Text(
                text = secondary,
                style = StillTypography.Small,
                color = StillColors.Gray,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(searching: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (searching) "no matches" else "no contacts yet — tap new",
            style = StillTypography.Caption,
            color = StillColors.DimGray,
            modifier = Modifier.padding(bottom = 80.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PermissionPrompt(onRequestRead: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "still-contacts needs read access — tap to grant",
            style = StillTypography.Body,
            color = StillColors.SoftWhite,
            modifier = Modifier
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onRequestRead,
                )
                .padding(bottom = 80.dp),
        )
    }
}

@Composable
private fun FooterBar(
    modifier: Modifier = Modifier,
    searching: Boolean,
    onSearchToggle: () -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StillVerb(
            text = if (searching) "cancel" else "search",
            onClick = onSearchToggle,
            bordered = true,
            color = if (searching) StillColors.SoftWhite else StillColors.MutedWhite,
        )
        StillVerb(
            text = "new",
            onClick = onNew,
            bordered = true,
            color = StillColors.SoftWhite,
        )
        StillVerb(
            text = "settings",
            onClick = onSettings,
            bordered = true,
            color = StillColors.MutedWhite,
        )
    }
}

/** '#' bucket sorts last; otherwise alphabetical. */
private object SectionLetterComparator : Comparator<Char> {
    override fun compare(a: Char, b: Char): Int {
        val aHash = a == '#'
        val bHash = b == '#'
        return when {
            aHash && bHash -> 0
            aHash -> 1
            bHash -> -1
            else -> a.compareTo(b)
        }
    }
}
