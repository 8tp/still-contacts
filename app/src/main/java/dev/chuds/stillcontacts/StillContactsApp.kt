package dev.chuds.stillcontacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chuds.stillcontacts.data.AccountTarget
import dev.chuds.stillcontacts.data.Contact
import dev.chuds.stillcontacts.data.ContactDetail
import dev.chuds.stillcontacts.data.ContactsRepository
import dev.chuds.stillcontacts.data.ContactsRepositoryImpl
import dev.chuds.stillcontacts.data.FontPreset
import dev.chuds.stillcontacts.data.NameDisplayOrder
import dev.chuds.stillcontacts.data.PreferencesRepository
import dev.chuds.stillcontacts.data.SortOrder
import dev.chuds.stillcontacts.data.StillContactsPreferences
import dev.chuds.stillcontacts.data.importVCardsFromUris
import dev.chuds.stillcontacts.data.safeContactFilename
import dev.chuds.stillcontacts.data.writeAllVCardsToUri
import dev.chuds.stillcontacts.data.writeSingleVCardToUri
import dev.chuds.stillcontacts.ui.detail.ContactDetailScreen
import dev.chuds.stillcontacts.ui.edit.ContactEditScreen
import dev.chuds.stillcontacts.ui.list.ContactsListScreen
import dev.chuds.stillcontacts.ui.settings.SettingsScreen
import dev.chuds.stillcontacts.ui.theme.LocalHaptics
import dev.chuds.stillcontacts.ui.theme.LocalStillTypography
import dev.chuds.stillcontacts.ui.theme.stillTypographyFor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Top-level composable. Hand-rolled router: list / detail / edit / settings.
 * Owns the SAF launchers, the permission requests, and the incoming-intent plumbing.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun StillContactsApp(
    pickMode: Boolean = false,
    incomingViewUri: Uri? = null,
    onPicked: (Uri) -> Unit = {},
) {
    val context = LocalContext.current
    val activityContext = context
    val appContext = context.applicationContext
    val repository: ContactsRepository = remember(appContext) { ContactsRepositoryImpl(appContext) }
    val prefs = remember(appContext) { PreferencesRepository(appContext) }
    val scope = rememberCoroutineScope()

    val settingsState = remember(prefs) {
        prefs.settings.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = StillContactsPreferences(),
        )
    }
    val settings by settingsState.collectAsStateWithLifecycle()

    var readGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.READ_CONTACTS,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var writeGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.WRITE_CONTACTS,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var pendingImportUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val readPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        readGranted = granted
    }
    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        writeGranted = granted
        if (!granted) {
            pendingImportUris = emptyList()
            Toast.makeText(activityContext, "write access denied", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!readGranted) readPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    val query = remember { MutableStateFlow("") }
    val queryText by query.collectAsStateWithLifecycle()

    // Re-query when the user toggles sortOrder or nameDisplayOrder.
    val contacts by remember(repository, settings.sortOrder, settings.nameDisplayOrder, readGranted) {
        if (!readGranted) MutableStateFlow(emptyList())
        else query.flatMapLatest { q ->
            repository.observeAll(
                sortOrder = settings.sortOrder,
                nameDisplayOrder = settings.nameDisplayOrder,
                query = q.takeIf { it.isNotBlank() },
            )
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList<Contact>())

    var route by remember { mutableStateOf<Route>(Route.List) }
    var actionTarget by remember { mutableStateOf<Contact?>(null) }
    var pendingExport by remember { mutableStateOf<ContactDetail?>(null) }
    var pendingBulkExport by remember { mutableStateOf(false) }
    var writableAccounts by remember { mutableStateOf<List<AccountTarget.Named>>(emptyList()) }

    LaunchedEffect(readGranted, writeGranted) {
        if (readGranted) writableAccounts = repository.listWritableAccounts()
    }

    // SAF launchers.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            pendingImportUris = uris
        }
    }
    val exportSingleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/vcard"),
    ) { uri ->
        val target = pendingExport
        pendingExport = null
        if (uri != null && target != null) {
            scope.launch {
                if (writeSingleVCardToUri(activityContext, uri, target)) {
                    Toast.makeText(activityContext, "exported", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val exportBulkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/vcard"),
    ) { uri ->
        val isBulk = pendingBulkExport
        pendingBulkExport = false
        if (uri != null && isBulk) {
            scope.launch {
                if (writeAllVCardsToUri(activityContext, uri, repository)) {
                    Toast.makeText(activityContext, "exported all", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ACTION_VIEW for a .vcf — enqueue once, then import after WRITE_CONTACTS is granted.
    LaunchedEffect(incomingViewUri) {
        pendingImportUris = incomingViewUri?.let(::listOf) ?: return@LaunchedEffect
    }

    LaunchedEffect(pendingImportUris, writeGranted, settings.accountTarget) {
        val uris = pendingImportUris
        if (uris.isEmpty()) return@LaunchedEffect
        if (!ensureWrite(writeGranted, writePermissionLauncher::launch)) return@LaunchedEffect
        val n = importVCardsFromUris(activityContext, uris, repository, settings.accountTarget)
        Toast.makeText(activityContext, "imported $n contacts", Toast.LENGTH_SHORT).show()
        pendingImportUris = emptyList()
    }

    fun startImport() {
        importLauncher.launch(arrayOf("text/vcard", "text/x-vcard", "text/plain", "application/octet-stream"))
    }
    fun startSingleExport(detail: ContactDetail) {
        pendingExport = detail
        exportSingleLauncher.launch(safeContactFilename(detail.contact.displayName.ifBlank { "contact" }))
    }
    fun startBulkExport() {
        pendingBulkExport = true
        val ts = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        exportBulkLauncher.launch("still-contacts-$ts.vcf")
    }

    BackHandler(enabled = route !is Route.List) { route = Route.List }

    val typography = remember(settings.fontPreset) { stillTypographyFor(settings.fontPreset) }
    val hapticFeedback = LocalHapticFeedback.current
    val hapticsEnabled = settings.hapticsEnabled
    val haptics: () -> Unit = remember(hapticFeedback, hapticsEnabled) {
        if (hapticsEnabled) {
            { hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        } else {
            { }
        }
    }

    CompositionLocalProvider(
        LocalStillTypography provides typography,
        LocalHaptics provides haptics,
    ) {
        when (val current = route) {
            Route.List -> {
                ContactsListScreen(
                    contacts = contacts,
                    permissionGranted = readGranted,
                    pickMode = pickMode,
                    query = queryText,
                    onQueryChange = { query.value = it },
                    onOpenContact = { c ->
                        if (pickMode) {
                            val pickedUri = Uri.withAppendedPath(
                                ContactsContract.Contacts.CONTENT_LOOKUP_URI,
                                Uri.encode(c.lookupKey),
                            )
                            onPicked(pickedUri)
                        } else {
                            route = Route.Detail(c.lookupKey, c.rawContactId)
                        }
                    },
                    onLongPressContact = { actionTarget = it },
                    onNew = {
                        if (!writeGranted) writePermissionLauncher.launch(Manifest.permission.WRITE_CONTACTS)
                        route = Route.New
                    },
                    onSettings = { route = Route.Settings },
                    onRequestRead = {
                        readPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    },
                )
            }
            is Route.Detail -> {
                var detailState by remember(current.lookupKey, current.rawContactId) {
                    mutableStateOf<ContactDetail?>(null)
                }
                LaunchedEffect(current.lookupKey, current.rawContactId, contacts) {
                    detailState = repository.getDetail(current.lookupKey, current.rawContactId)
                }
                detailState?.let { d ->
                    ContactDetailScreen(
                        detail = d,
                        onBack = { route = Route.List },
                        onEdit = {
                            if (!writeGranted) writePermissionLauncher.launch(Manifest.permission.WRITE_CONTACTS)
                            route = Route.Edit(current.lookupKey, current.rawContactId)
                        },
                        onExport = { startSingleExport(d) },
                    )
                }
            }
            is Route.Edit -> {
                var detailState by remember(current.lookupKey, current.rawContactId) {
                    mutableStateOf<ContactDetail?>(null)
                }
                LaunchedEffect(current.lookupKey, current.rawContactId) {
                    detailState = repository.getDetail(current.lookupKey, current.rawContactId)
                }
                detailState?.let { d ->
                    ContactEditScreen(
                        initial = d,
                        onSave = { edited ->
                            scope.launch {
                                if (!ensureWrite(writeGranted, writePermissionLauncher::launch)) return@launch
                                repository.update(d.contact.rawContactId, edited)
                                route = Route.List
                            }
                        },
                        onDelete = {
                            scope.launch {
                                if (!ensureWrite(writeGranted, writePermissionLauncher::launch)) return@launch
                                repository.delete(d.contact.rawContactId)
                                route = Route.List
                            }
                        },
                        onCancel = { route = Route.List },
                    )
                }
            }
            Route.New -> {
                ContactEditScreen(
                    initial = null,
                    onSave = { edited ->
                        scope.launch {
                            if (!ensureWrite(writeGranted, writePermissionLauncher::launch)) return@launch
                            repository.create(edited, settings.accountTarget)
                            route = Route.List
                        }
                    },
                    onDelete = null,
                    onCancel = { route = Route.List },
                )
            }
            Route.Settings -> {
                SettingsScreen(
                    settings = settings,
                    writableAccounts = writableAccounts,
                    onCycleFontPreset = {
                        scope.launch {
                            val next = when (settings.fontPreset) {
                                FontPreset.System -> FontPreset.Editorial
                                FontPreset.Editorial -> FontPreset.Terminal
                                FontPreset.Terminal -> FontPreset.Grotesk
                                FontPreset.Grotesk -> FontPreset.System
                            }
                            prefs.setFontPreset(next)
                        }
                    },
                    onCycleNameDisplayOrder = {
                        scope.launch {
                            val next = when (settings.nameDisplayOrder) {
                                NameDisplayOrder.GivenFamily -> NameDisplayOrder.FamilyGiven
                                NameDisplayOrder.FamilyGiven -> NameDisplayOrder.System
                                NameDisplayOrder.System -> NameDisplayOrder.GivenFamily
                            }
                            prefs.setNameDisplayOrder(next)
                        }
                    },
                    onCycleSortOrder = {
                        scope.launch {
                            val next = when (settings.sortOrder) {
                                SortOrder.Given -> SortOrder.Family
                                SortOrder.Family -> SortOrder.Given
                            }
                            prefs.setSortOrder(next)
                        }
                    },
                    onToggleHaptics = {
                        scope.launch { prefs.setHapticsEnabled(!settings.hapticsEnabled) }
                    },
                    onPickAccount = { picked ->
                        scope.launch {
                            when (picked) {
                                AccountTarget.PhoneOnly -> prefs.setDefaultAccount(null, null)
                                is AccountTarget.Named -> prefs.setDefaultAccount(picked.name, picked.type)
                            }
                        }
                    },
                    onImport = ::startImport,
                    onExportAll = ::startBulkExport,
                    onDeleteAll = {
                        scope.launch {
                            if (!ensureWrite(writeGranted, writePermissionLauncher::launch)) return@launch
                            val n = repository.deleteAllStillContactsRaws(settings.accountTarget)
                            Toast.makeText(
                                activityContext,
                                "deleted $n still-contacts entries",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onBack = { route = Route.List },
                )
            }
        }
    }

    actionTarget?.let { c ->
        ContactActionSheet(
            displayName = c.displayName,
            onOpen = {
                actionTarget = null
                route = Route.Detail(c.lookupKey, c.rawContactId)
            },
            onEdit = {
                if (!writeGranted) writePermissionLauncher.launch(Manifest.permission.WRITE_CONTACTS)
                actionTarget = null
                route = Route.Edit(c.lookupKey, c.rawContactId)
            },
            onExport = {
                actionTarget = null
                scope.launch {
                    val d = repository.getDetail(c.lookupKey, c.rawContactId) ?: return@launch
                    startSingleExport(d)
                }
            },
            onDelete = {
                actionTarget = null
                scope.launch {
                    if (!ensureWrite(writeGranted, writePermissionLauncher::launch)) return@launch
                    repository.delete(c.rawContactId)
                }
            },
            onDismiss = { actionTarget = null },
        )
    }
}

private fun ensureWrite(granted: Boolean, request: (String) -> Unit): Boolean {
    if (!granted) {
        request(Manifest.permission.WRITE_CONTACTS)
        return false
    }
    return true
}

private sealed interface Route {
    data object List : Route
    data class Detail(val lookupKey: String, val rawContactId: Long) : Route
    data class Edit(val lookupKey: String, val rawContactId: Long) : Route
    data object New : Route
    data object Settings : Route
}

/** Pulls an incoming ACTION_VIEW vCard URI off the Activity intent (consumed once). */
fun ComponentActivity.consumeViewVCardUriIfAny(): Uri? {
    val intent = intent ?: return null
    if (intent.action != Intent.ACTION_VIEW) return null
    val type = intent.type
    if (type != "text/vcard" && type != "text/x-vcard") return null
    val uri = intent.data ?: return null
    intent.action = null
    return uri
}

/** Detects whether the launching intent asks us to act as a contact picker. */
fun ComponentActivity.isPickMode(): Boolean {
    val intent = intent ?: return false
    return intent.action == Intent.ACTION_PICK &&
        intent.type == ContactsContract.Contacts.CONTENT_TYPE
}
