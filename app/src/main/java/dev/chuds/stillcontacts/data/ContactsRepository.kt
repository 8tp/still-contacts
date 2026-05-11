package dev.chuds.stillcontacts.data

/*
 * ContactsRepository — the only thing that touches ContentResolver.
 *
 * Why ContactsContract is the source of truth (and not flat .vcf files in filesDir/):
 *   A future still-dial / still-sms (and the user's existing dialer / SMS app today) needs
 *   to resolve incoming numbers against names. Android exposes exactly one mechanism for
 *   this — the ContactsContract provider. Storing data anywhere else means the phone rings
 *   with a bare number every time. The pact is honored differently: bulk plaintext .vcf
 *   export is the load-bearing promise (see IoActions and SettingsScreen).
 *
 * Why update is "delete all Data rows + re-insert" rather than diffing:
 *   Diffing edited rows against existing rows by mimetype + index is fragile (the user can
 *   reorder, delete, re-add). Re-inserting all Data rows for the rawContactId in the same
 *   applyBatch transaction is atomic and trivial to audit. The trade-off is a few extra
 *   row inserts per save, which is negligible at human edit cadence.
 *
 * Why every RawContacts insert carries SOURCE_ID = "still-contacts":
 *   The settings screen's "delete all" only deletes rows that carry this sentinel, so the
 *   user can never accidentally wipe contacts created by another app sharing the same
 *   account. The sentinel is the entire safety story for "delete all".
 */

import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val STILL_CONTACTS_SOURCE_ID = "still-contacts"

private const val OBSERVER_DEBOUNCE_MS = 100L

interface ContactsRepository {
    fun observeAll(
        sortOrder: SortOrder,
        nameDisplayOrder: NameDisplayOrder,
        query: String? = null,
    ): Flow<List<Contact>>

    suspend fun getDetail(lookupKey: String, rawContactId: Long? = null): ContactDetail?
    suspend fun getAggregateDetail(lookupKey: String): ContactDetail?
    suspend fun create(detail: ContactDetail, account: AccountTarget): Long
    suspend fun update(rawContactId: Long, detail: ContactDetail)
    suspend fun delete(rawContactId: Long)
    suspend fun importBatch(details: List<ContactDetail>, account: AccountTarget): Int
    suspend fun deleteAllStillContactsRaws(account: AccountTarget): Int
    suspend fun listWritableAccounts(): List<AccountTarget.Named>
    suspend fun lookupRawContactId(lookupKey: String): Long?
    suspend fun isStillContactRaw(rawContactId: Long): Boolean
    suspend fun loadAllForExport(): List<ContactDetail>
}

class ContactsRepositoryImpl(context: Context) : ContactsRepository {

    private val appContext: Context = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    override fun observeAll(
        sortOrder: SortOrder,
        nameDisplayOrder: NameDisplayOrder,
        query: String?,
    ): Flow<List<Contact>> = callbackFlow {
        // A tick channel — the ContentObserver only signals "something changed";
        // we re-query on a debounced timer rather than on every onChange.
        val ticks = Channel<Unit>(capacity = Channel.CONFLATED)

        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                ticks.trySend(Unit)
            }
        }
        resolver.registerContentObserver(Contacts.CONTENT_URI, true, observer)

        val scope = MainScope()
        var refreshJob: Job? = null

        fun refresh() {
            refreshJob?.cancel()
            refreshJob = scope.launch(Dispatchers.IO) {
                runCatching {
                    val list = queryAll(sortOrder, nameDisplayOrder, query)
                    if (isActive) trySend(list)
                }
            }
        }

        // Initial emission.
        refresh()

        scope.launch {
            for (signal in ticks) {
                delay(OBSERVER_DEBOUNCE_MS)
                refresh()
            }
        }

        awaitClose {
            resolver.unregisterContentObserver(observer)
            scope.cancel()
        }
    }.flowOn(Dispatchers.Default).distinctUntilChanged()

    private fun queryAll(
        sortOrder: SortOrder,
        nameDisplayOrder: NameDisplayOrder,
        query: String?,
    ): List<Contact> {
        val sortKey = when (sortOrder) {
            SortOrder.Given -> Contacts.SORT_KEY_PRIMARY
            SortOrder.Family -> Contacts.SORT_KEY_ALTERNATIVE
        }
        val displayNameColumn = when (nameDisplayOrder) {
            NameDisplayOrder.GivenFamily,
            NameDisplayOrder.System -> Contacts.DISPLAY_NAME_PRIMARY
            NameDisplayOrder.FamilyGiven -> Contacts.DISPLAY_NAME_ALTERNATIVE
        }

        val projection = arrayOf(
            Contacts._ID,
            Contacts.LOOKUP_KEY,
            displayNameColumn,
            sortKey,
        )

        val contacts = mutableListOf<Contact>()
        resolver.query(
            Contacts.CONTENT_URI,
            projection,
            null,
            null,
            "$sortKey COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(Contacts._ID)
            val lookupIdx = cursor.getColumnIndexOrThrow(Contacts.LOOKUP_KEY)
            val nameIdx = cursor.getColumnIndexOrThrow(displayNameColumn)
            val sortIdx = cursor.getColumnIndexOrThrow(sortKey)
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(idIdx)
                val lookupKey = cursor.getString(lookupIdx) ?: continue
                val displayName = cursor.getString(nameIdx) ?: ""
                val sortValue = cursor.getString(sortIdx) ?: displayName
                val sectionLetter = sectionLetterFor(sortValue)

                // primary phone / email — one extra small lookup. For 500-row lists this
                // is fine; if it ever isn't, this is the spot to cache.
                val rawContactId = firstRawContactIdFor(contactId) ?: continue
                val phone = primaryPhoneFor(contactId)
                val email = if (phone == null) primaryEmailFor(contactId) else null
                val (given, family) = givenFamilyFor(contactId)

                contacts += Contact(
                    rawContactId = rawContactId,
                    lookupKey = lookupKey,
                    displayName = displayName,
                    familyName = family,
                    givenName = given,
                    primaryPhone = phone,
                    primaryEmail = email,
                    sectionLetter = sectionLetter,
                )
            }
        }

        if (query.isNullOrBlank()) return contacts
        val q = query.trim().lowercase()
        return contacts.filter { c ->
            c.displayName.lowercase().contains(q) ||
                c.primaryPhone?.let { p -> normalizePhoneForSearch(p).contains(normalizePhoneForSearch(q)) } == true ||
                c.primaryEmail?.lowercase()?.contains(q) == true ||
                phoneOrEmailRowsContain(c.lookupKey, q)
        }
    }

    private fun sectionLetterFor(sortValue: String): Char {
        val first = sortValue.trimStart().firstOrNull() ?: '#'
        return if (first.isLetter()) first.uppercaseChar() else '#'
    }

    private fun firstRawContactIdFor(contactId: Long): Long? {
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID),
            "${RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            "${RawContacts._ID} ASC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return null
    }

    private fun primaryPhoneFor(contactId: Long): String? {
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(CommonDataKinds.Phone.NUMBER, Data.IS_SUPER_PRIMARY, Data.IS_PRIMARY),
            "${Data.CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
            arrayOf(contactId.toString(), CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
            "${Data.IS_SUPER_PRIMARY} DESC, ${Data.IS_PRIMARY} DESC, ${Data._ID} ASC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val v = cursor.getString(0)
                if (!v.isNullOrBlank()) return v
            }
        }
        return null
    }

    private fun primaryEmailFor(contactId: Long): String? {
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(CommonDataKinds.Email.ADDRESS, Data.IS_SUPER_PRIMARY, Data.IS_PRIMARY),
            "${Data.CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
            arrayOf(contactId.toString(), CommonDataKinds.Email.CONTENT_ITEM_TYPE),
            "${Data.IS_SUPER_PRIMARY} DESC, ${Data.IS_PRIMARY} DESC, ${Data._ID} ASC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val v = cursor.getString(0)
                if (!v.isNullOrBlank()) return v
            }
        }
        return null
    }

    private fun givenFamilyFor(contactId: Long): Pair<String?, String?> {
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(CommonDataKinds.StructuredName.GIVEN_NAME, CommonDataKinds.StructuredName.FAMILY_NAME),
            "${Data.CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
            arrayOf(contactId.toString(), CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0) to cursor.getString(1)
        }
        return null to null
    }

    private fun phoneOrEmailRowsContain(lookupKey: String, q: String): Boolean {
        val contactId = contactIdForLookup(lookupKey) ?: return false
        // Search every phone & email — useful for a contact whose primary doesn't match.
        val qNorm = normalizePhoneForSearch(q)
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.MIMETYPE, CommonDataKinds.Phone.NUMBER, CommonDataKinds.Email.ADDRESS),
            "${Data.CONTACT_ID} = ? AND (${Data.MIMETYPE} = ? OR ${Data.MIMETYPE} = ?)",
            arrayOf(
                contactId.toString(),
                CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            ),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val mimeType = cursor.getString(0)
                if (mimeType == CommonDataKinds.Phone.CONTENT_ITEM_TYPE) {
                    val n = cursor.getString(1)
                    if (n != null && normalizePhoneForSearch(n).contains(qNorm)) return true
                } else {
                    val e = cursor.getString(2)
                    if (e != null && e.lowercase().contains(q)) return true
                }
            }
        }
        return false
    }

    private fun contactIdForLookup(lookupKey: String): Long? {
        val uri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, Uri.encode(lookupKey))
        resolver.query(uri, arrayOf(Contacts._ID), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return null
    }

    private fun normalizePhoneForSearch(phone: String): String =
        phone.filter { it.isDigit() }

    override suspend fun getDetail(
        lookupKey: String,
        rawContactId: Long?,
    ): ContactDetail? = withContext(Dispatchers.IO) {
        val contactId = contactIdForLookup(lookupKey) ?: return@withContext null
        val selectedRawContactId = if (rawContactId != null) {
            rawContactId
                .takeIf { rawContactBelongsToContact(it, contactId) }
                ?: return@withContext null
        } else {
            firstRawContactIdFor(contactId) ?: return@withContext null
        }

        readDetailRows(
            lookupKey = lookupKey,
            rawContactId = selectedRawContactId,
            dataSelection = rawContactDataSelection(selectedRawContactId),
            displayNameFallback = displayNameForContactId(contactId),
        )
    }

    override suspend fun getAggregateDetail(lookupKey: String): ContactDetail? = withContext(Dispatchers.IO) {
        val contactId = contactIdForLookup(lookupKey) ?: return@withContext null
        val rawContactId = firstRawContactIdFor(contactId) ?: return@withContext null
        readDetailRows(
            lookupKey = lookupKey,
            rawContactId = rawContactId,
            dataSelection = aggregateContactDataSelection(contactId),
            displayNameFallback = displayNameForContactId(contactId),
        )
    }

    private fun readDetailRows(
        lookupKey: String,
        rawContactId: Long,
        dataSelection: ContactDataSelection,
        displayNameFallback: String? = null,
    ): ContactDetail {
        var displayName = ""
        var given: String? = null
        var family: String? = null
        val phones = mutableListOf<TypedValue>()
        val emails = mutableListOf<TypedValue>()
        val addresses = mutableListOf<TypedValue>()
        var organization: String? = null
        var notes: String? = null
        var birthday: LocalDate? = null

        resolver.query(
            Data.CONTENT_URI,
            null,
            dataSelection.selection,
            dataSelection.selectionArgs.toTypedArray(),
            null,
        )?.use { cursor ->
            val mimeIdx = cursor.getColumnIndexOrThrow(Data.MIMETYPE)
            while (cursor.moveToNext()) {
                when (cursor.getString(mimeIdx)) {
                    CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                        val rowGiven = cursor.getStringSafe(CommonDataKinds.StructuredName.GIVEN_NAME)
                        val rowFamily = cursor.getStringSafe(CommonDataKinds.StructuredName.FAMILY_NAME)
                        if (given.isNullOrBlank()) given = rowGiven
                        if (family.isNullOrBlank()) family = rowFamily
                        if (displayName.isBlank()) {
                            displayName = cursor.getStringSafe(CommonDataKinds.StructuredName.DISPLAY_NAME)
                                ?.takeIf { it.isNotBlank() }
                                ?: listOf(rowGiven, rowFamily)
                                    .filter { !it.isNullOrBlank() }
                                    .joinToString(" ")
                        }
                    }
                    CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        val number = cursor.getStringSafe(CommonDataKinds.Phone.NUMBER) ?: continue
                        val type = cursor.getIntSafe(CommonDataKinds.Phone.TYPE)
                        phones += TypedValue(TypeLabel.fromPhoneType(type), number)
                    }
                    CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                        val addr = cursor.getStringSafe(CommonDataKinds.Email.ADDRESS) ?: continue
                        val type = cursor.getIntSafe(CommonDataKinds.Email.TYPE)
                        emails += TypedValue(TypeLabel.fromEmailType(type), addr)
                    }
                    CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                        val formatted = cursor.getStringSafe(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS)
                            ?: cursor.getStringSafe(CommonDataKinds.StructuredPostal.STREET)
                            ?: continue
                        val type = cursor.getIntSafe(CommonDataKinds.StructuredPostal.TYPE)
                        addresses += TypedValue(TypeLabel.fromAddressType(type), formatted)
                    }
                    CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                        organization = cursor.getStringSafe(CommonDataKinds.Organization.COMPANY)
                    }
                    CommonDataKinds.Note.CONTENT_ITEM_TYPE -> {
                        notes = cursor.getStringSafe(CommonDataKinds.Note.NOTE)
                    }
                    CommonDataKinds.Event.CONTENT_ITEM_TYPE -> {
                        val type = cursor.getIntSafe(CommonDataKinds.Event.TYPE)
                        if (type == CommonDataKinds.Event.TYPE_BIRTHDAY) {
                            birthday = parseBirthday(cursor.getStringSafe(CommonDataKinds.Event.START_DATE))
                        }
                    }
                }
            }
        }

        val resolvedDisplayName = resolveContactDisplayName(
            dataDisplayName = displayName,
            fallbackDisplayName = displayNameFallback,
        )

        return ContactDetail(
            contact = Contact(
                rawContactId = rawContactId,
                lookupKey = lookupKey,
                displayName = resolvedDisplayName,
                familyName = family,
                givenName = given,
                primaryPhone = phones.firstOrNull()?.value,
                primaryEmail = emails.firstOrNull()?.value,
                sectionLetter = sectionLetterFor(resolvedDisplayName),
            ),
            phones = phones,
            emails = emails,
            addresses = addresses,
            organization = organization,
            notes = notes,
            birthday = birthday,
        )
    }

    private fun rawContactBelongsToContact(rawContactId: Long, contactId: Long): Boolean {
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID),
            "${RawContacts._ID} = ? AND ${RawContacts.CONTACT_ID} = ?",
            arrayOf(rawContactId.toString(), contactId.toString()),
            null,
        )?.use { cursor ->
            return cursor.moveToFirst()
        }
        return false
    }

    private fun displayNameForContactId(contactId: Long): String? {
        resolver.query(
            Contacts.CONTENT_URI,
            arrayOf(Contacts.DISPLAY_NAME_PRIMARY),
            "${Contacts._ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)?.takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun parseBirthday(raw: String?): LocalDate? {
        val s = raw?.trim() ?: return null
        if (s.isEmpty()) return null
        // ContactsContract stores the user-entered text; vCard 3.0 commonly uses yyyy-MM-dd
        // or yyyyMMdd. Try the common variants and surrender quietly on anything else.
        return runCatching { LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }.getOrNull()
            ?: runCatching { LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyyMMdd")) }.getOrNull()
            ?: runCatching { LocalDate.parse(s) }.getOrNull()
    }

    override suspend fun create(detail: ContactDetail, account: AccountTarget): Long =
        withContext(Dispatchers.IO) {
            val ops = ArrayList<ContentProviderOperation>()
            ops += newRawContactInsert(account)
            appendDataInserts(ops, rawContactBackref = 0, detail)
            val results = resolver.applyBatch(ContactsContract.AUTHORITY, ops)
            ContentUris.parseId(results[0].uri ?: return@withContext -1L)
        }

    override suspend fun update(rawContactId: Long, detail: ContactDetail) =
        withContext<Unit>(Dispatchers.IO) {
            if (!isStillContactRawInternal(rawContactId)) return@withContext
            // Re-insert Still-supported rows while preserving unsupported provider data
            // such as photos, websites, nicknames, and custom mimetypes.
            val replaceSelection = supportedDataReplaceSelection(rawContactId)
            val ops = ArrayList<ContentProviderOperation>()
            ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection(
                    replaceSelection.selection,
                    replaceSelection.selectionArgs.toTypedArray(),
                )
                .build()
            appendDataInsertsForExistingRaw(ops, rawContactId, detail)
            resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        }

    override suspend fun delete(rawContactId: Long) = withContext<Unit>(Dispatchers.IO) {
        if (!isStillContactRawInternal(rawContactId)) return@withContext
        // CALLER_IS_SYNCADAPTER=true → hard delete. Without it the provider keeps the row
        // around as a "deleted=1" tombstone for sync-engine reconciliation, which we do
        // not want because we are not a sync adapter.
        val uri = RawContacts.CONTENT_URI.buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .build()
        resolver.delete(uri, "${RawContacts._ID} = ?", arrayOf(rawContactId.toString()))
    }

    override suspend fun importBatch(
        details: List<ContactDetail>,
        account: AccountTarget,
    ): Int = withContext(Dispatchers.IO) {
        var inserted = 0
        // applyBatch has practical size limits (a few hundred ops per call). Process each
        // contact's worth of ops as its own batch — slower per call, but simpler and safe
        // for arbitrarily large imports.
        details.forEach { detail ->
            try {
                val ops = ArrayList<ContentProviderOperation>()
                ops += newRawContactInsert(account)
                appendDataInserts(ops, rawContactBackref = 0, detail)
                resolver.applyBatch(ContactsContract.AUTHORITY, ops)
                inserted++
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // skip bad rows; the rest of the import still lands.
            }
        }
        inserted
    }

    override suspend fun deleteAllStillContactsRaws(account: AccountTarget): Int =
        withContext(Dispatchers.IO) {
            val deleteSelection = stillContactsDeleteSelection(account)
            val uri = RawContacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                .build()
            resolver.delete(
                uri,
                deleteSelection.selection,
                deleteSelection.selectionArgs.toTypedArray(),
            )
        }

    override suspend fun listWritableAccounts(): List<AccountTarget.Named> =
        withContext(Dispatchers.IO) {
            // We do not declare GET_ACCOUNTS — AccountManager.getAccounts() will return an
            // empty array on most modern Android versions without that permission. Fall back
            // to "the accounts already known to the contacts provider" via RawContacts.
            val seen = LinkedHashMap<String, AccountTarget.Named>()
            resolver.query(
                RawContacts.CONTENT_URI,
                arrayOf(RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
                "${RawContacts.ACCOUNT_NAME} IS NOT NULL AND ${RawContacts.ACCOUNT_TYPE} IS NOT NULL",
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0) ?: continue
                    val type = cursor.getString(1) ?: continue
                    val key = "$name|$type"
                    seen.getOrPut(key) { AccountTarget.Named(name, type) }
                }
            }
            // AccountManager.getAccounts() — in case GET_ACCOUNTS isn't required for the
            // user's targeted Android version (older API levels) we still try it.
            runCatching {
                val am = AccountManager.get(appContext)
                am.accounts.forEach { acct ->
                    val key = "${acct.name}|${acct.type}"
                    seen.getOrPut(key) { AccountTarget.Named(acct.name, acct.type) }
                }
            }
            seen.values.toList()
        }

    override suspend fun lookupRawContactId(lookupKey: String): Long? =
        withContext(Dispatchers.IO) {
            val contactId = contactIdForLookup(lookupKey) ?: return@withContext null
            firstRawContactIdFor(contactId)
        }

    override suspend fun isStillContactRaw(rawContactId: Long): Boolean =
        withContext(Dispatchers.IO) { isStillContactRawInternal(rawContactId) }

    private fun isStillContactRawInternal(rawContactId: Long): Boolean {
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID),
            "${RawContacts._ID} = ? AND ${RawContacts.SOURCE_ID} = ?",
            arrayOf(rawContactId.toString(), STILL_CONTACTS_SOURCE_ID),
            null,
        )?.use { cursor ->
            return cursor.moveToFirst()
        }
        return false
    }

    override suspend fun loadAllForExport(): List<ContactDetail> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ContactDetail>()
        resolver.query(
            Contacts.CONTENT_URI,
            arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(0)
                val key = cursor.getString(1) ?: continue
                val displayNameFallback = cursor.getString(2)
                val rawContactId = firstRawContactIdFor(contactId) ?: continue
                list += readDetailRows(
                    lookupKey = key,
                    rawContactId = rawContactId,
                    dataSelection = aggregateContactDataSelection(contactId),
                    displayNameFallback = displayNameFallback,
                )
            }
        }
        list
    }

    private fun newRawContactInsert(account: AccountTarget): ContentProviderOperation {
        val builder = ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.SOURCE_ID, STILL_CONTACTS_SOURCE_ID)
        when (account) {
            is AccountTarget.PhoneOnly -> {
                builder.withValue(RawContacts.ACCOUNT_NAME, null)
                builder.withValue(RawContacts.ACCOUNT_TYPE, null)
            }
            is AccountTarget.Named -> {
                builder.withValue(RawContacts.ACCOUNT_NAME, account.name)
                builder.withValue(RawContacts.ACCOUNT_TYPE, account.type)
            }
        }
        return builder.build()
    }

    /** Inserts referencing the just-inserted RawContacts via back-reference index. */
    private fun appendDataInserts(
        ops: ArrayList<ContentProviderOperation>,
        rawContactBackref: Int,
        detail: ContactDetail,
    ) {
        // Structured name
        if (!detail.contact.givenName.isNullOrBlank() || !detail.contact.familyName.isNullOrBlank() || detail.contact.displayName.isNotBlank()) {
            ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, rawContactBackref)
                .withValue(Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.StructuredName.GIVEN_NAME, detail.contact.givenName)
                .withValue(CommonDataKinds.StructuredName.FAMILY_NAME, detail.contact.familyName)
                .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, detail.contact.displayName.ifBlank { null })
                .build()
        }

        appendCommonInserts(ops, rawContactBackref, useBackref = true, rawContactId = -1L, detail)
    }

    /** Inserts referencing an existing RawContacts row by id (used by update). */
    private fun appendDataInsertsForExistingRaw(
        ops: ArrayList<ContentProviderOperation>,
        rawContactId: Long,
        detail: ContactDetail,
    ) {
        if (!detail.contact.givenName.isNullOrBlank() || !detail.contact.familyName.isNullOrBlank() || detail.contact.displayName.isNotBlank()) {
            ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValue(Data.RAW_CONTACT_ID, rawContactId)
                .withValue(Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.StructuredName.GIVEN_NAME, detail.contact.givenName)
                .withValue(CommonDataKinds.StructuredName.FAMILY_NAME, detail.contact.familyName)
                .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, detail.contact.displayName.ifBlank { null })
                .build()
        }
        appendCommonInserts(ops, backref = -1, useBackref = false, rawContactId = rawContactId, detail)
    }

    private fun appendCommonInserts(
        ops: ArrayList<ContentProviderOperation>,
        backref: Int,
        useBackref: Boolean,
        rawContactId: Long,
        detail: ContactDetail,
    ) {
        fun newInsertBuilder() = ContentProviderOperation.newInsert(Data.CONTENT_URI).apply {
            if (useBackref) withValueBackReference(Data.RAW_CONTACT_ID, backref)
            else withValue(Data.RAW_CONTACT_ID, rawContactId)
        }

        detail.phones.forEach { tv ->
            ops += newInsertBuilder()
                .withValue(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.Phone.NUMBER, tv.value)
                .withValue(CommonDataKinds.Phone.TYPE, tv.type.phoneType)
                .build()
        }

        detail.emails.forEach { tv ->
            ops += newInsertBuilder()
                .withValue(Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.Email.ADDRESS, tv.value)
                .withValue(CommonDataKinds.Email.TYPE, tv.type.emailType)
                .build()
        }

        detail.addresses.forEach { tv ->
            ops += newInsertBuilder()
                .withValue(Data.MIMETYPE, CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, tv.value)
                .withValue(CommonDataKinds.StructuredPostal.TYPE, tv.type.addressType)
                .build()
        }

        detail.organization?.takeIf { it.isNotBlank() }?.let { org ->
            ops += newInsertBuilder()
                .withValue(Data.MIMETYPE, CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.Organization.COMPANY, org)
                .withValue(CommonDataKinds.Organization.TYPE, CommonDataKinds.Organization.TYPE_WORK)
                .build()
        }

        detail.notes?.takeIf { it.isNotBlank() }?.let { note ->
            ops += newInsertBuilder()
                .withValue(Data.MIMETYPE, CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.Note.NOTE, note)
                .build()
        }

        detail.birthday?.let { date ->
            ops += newInsertBuilder()
                .withValue(Data.MIMETYPE, CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.Event.START_DATE, date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .withValue(CommonDataKinds.Event.TYPE, CommonDataKinds.Event.TYPE_BIRTHDAY)
                .build()
        }
    }
}

private fun android.database.Cursor.getStringSafe(column: String): String? {
    val idx = getColumnIndex(column)
    if (idx < 0) return null
    if (isNull(idx)) return null
    return getString(idx)
}

private fun android.database.Cursor.getIntSafe(column: String): Int {
    val idx = getColumnIndex(column)
    if (idx < 0) return 0
    if (isNull(idx)) return 0
    return getInt(idx)
}

internal fun resolveContactDisplayName(
    dataDisplayName: String,
    fallbackDisplayName: String?,
): String = dataDisplayName.ifBlank { fallbackDisplayName.orEmpty() }
