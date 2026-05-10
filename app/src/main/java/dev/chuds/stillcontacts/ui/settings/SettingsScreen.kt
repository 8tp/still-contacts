package dev.chuds.stillcontacts.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chuds.stillcontacts.data.AccountTarget
import dev.chuds.stillcontacts.data.FontPreset
import dev.chuds.stillcontacts.data.NameDisplayOrder
import dev.chuds.stillcontacts.data.SortOrder
import dev.chuds.stillcontacts.data.StillContactsPreferences
import dev.chuds.stillcontacts.ui.components.StillMenuItem
import dev.chuds.stillcontacts.ui.components.StillSectionCard
import dev.chuds.stillcontacts.ui.theme.StillColors
import dev.chuds.stillcontacts.ui.theme.StillTypography
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    settings: StillContactsPreferences,
    contactsCount: Int,
    writableAccounts: List<AccountTarget.Named>,
    onCycleFontPreset: () -> Unit,
    onCycleNameDisplayOrder: () -> Unit,
    onCycleSortOrder: () -> Unit,
    onPickAccount: (AccountTarget) -> Unit,
    onImport: () -> Unit,
    onExportAll: () -> Unit,
    onDeleteAll: () -> Unit,
    onBack: () -> Unit,
) {
    var showAccounts by remember { mutableStateOf(false) }
    var deleteArmed by remember { mutableStateOf(false) }

    LaunchedEffect(deleteArmed) {
        if (deleteArmed) {
            delay(4_000)
            deleteArmed = false
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
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 96.dp),
        ) {
            Text(
                text = "settings",
                style = StillTypography.Title,
                color = StillColors.SoftWhite,
            )
            Text(
                text = "still contacts · v0.1.0",
                style = StillTypography.Caption,
                color = StillColors.DimGray,
                modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
            )

            StillSectionCard {
                StillMenuItem(
                    title = "font",
                    subtitle = settings.fontPreset.label(),
                    onClick = onCycleFontPreset,
                )
                StillMenuItem(
                    title = "name display",
                    subtitle = settings.nameDisplayOrder.label(),
                    onClick = onCycleNameDisplayOrder,
                )
                StillMenuItem(
                    title = "sort order",
                    subtitle = settings.sortOrder.label(),
                    onClick = onCycleSortOrder,
                )
            }

            Spacer(Modifier.height(20.dp))

            StillSectionCard {
                StillMenuItem(
                    title = "default account",
                    subtitle = settings.accountTarget.subtitle(),
                    onClick = { showAccounts = !showAccounts },
                )
                if (showAccounts) {
                    AccountChoice(
                        label = "phone-only",
                        active = settings.accountTarget is AccountTarget.PhoneOnly,
                        onClick = {
                            onPickAccount(AccountTarget.PhoneOnly)
                            showAccounts = false
                        },
                    )
                    writableAccounts.forEach { acct ->
                        AccountChoice(
                            label = "${acct.name} · ${acct.type}",
                            active = (settings.accountTarget as? AccountTarget.Named)?.name == acct.name,
                            onClick = {
                                onPickAccount(acct)
                                showAccounts = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            StillSectionCard {
                StillMenuItem(
                    title = "import .vcf",
                    subtitle = "pick one or more vcards from the file picker",
                    onClick = onImport,
                )
                StillMenuItem(
                    title = "export all",
                    subtitle = "save every contact as a single .vcf",
                    onClick = onExportAll,
                )
                StillMenuItem(
                    title = if (deleteArmed) "delete all" else "delete all",
                    subtitle = if (deleteArmed) "tap again to confirm"
                    else "removes only contacts created by still-contacts",
                    titleColor = if (deleteArmed) StillColors.SoftWhite else StillColors.MutedWhite,
                    onClick = {
                        if (deleteArmed) {
                            deleteArmed = false
                            onDeleteAll()
                        } else {
                            deleteArmed = true
                        }
                    },
                )
            }

            Spacer(Modifier.height(20.dp))

            StillSectionCard {
                StillMenuItem(
                    title = "ContactsContract is the source of truth",
                    subtitle = "$contactsCount in the system provider",
                    enabled = false,
                    onClick = {},
                )
                StillMenuItem(
                    title = "no internet",
                    subtitle = "the manifest declares no network permission",
                    enabled = false,
                    onClick = {},
                )
                StillMenuItem(
                    title = "no analytics",
                    subtitle = "no telemetry, no firebase, no play services",
                    enabled = false,
                    onClick = {},
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "still ecosystem",
                style = StillTypography.Caption,
                color = StillColors.Gray,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row {
                Text("launcher", style = StillTypography.Caption, color = StillColors.DimGray)
                Spacer(Modifier.padding(horizontal = 8.dp))
                Text("notes", style = StillTypography.Caption, color = StillColors.DimGray)
                Spacer(Modifier.padding(horizontal = 8.dp))
                Text("cal", style = StillTypography.Caption, color = StillColors.DimGray)
            }
        }

        FooterBar(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .navigationBarsPadding(),
            onBack = onBack,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountChoice(label: String, active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (active) "→ $label" else "  $label",
            style = StillTypography.Caption,
            color = if (active) StillColors.SoftWhite else StillColors.MutedWhite,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FooterBar(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "back",
            style = StillTypography.Menu,
            color = StillColors.MutedWhite,
            modifier = Modifier.combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onBack,
            ),
        )
    }
}

private fun FontPreset.label(): String = when (this) {
    FontPreset.System -> "system — serif + sans + mono"
    FontPreset.Editorial -> "editorial — cormorant + inter + plex"
    FontPreset.Terminal -> "terminal — plex mono throughout"
    FontPreset.Grotesk -> "grotesk — instrument serif + space"
}

private fun NameDisplayOrder.label(): String = when (this) {
    NameDisplayOrder.GivenFamily -> "given family"
    NameDisplayOrder.FamilyGiven -> "family, given"
    NameDisplayOrder.System -> "system"
}

private fun SortOrder.label(): String = when (this) {
    SortOrder.Given -> "given"
    SortOrder.Family -> "family"
}

private fun AccountTarget.subtitle(): String = when (this) {
    AccountTarget.PhoneOnly -> "phone-only — local"
    is AccountTarget.Named -> "$name · $type"
}
