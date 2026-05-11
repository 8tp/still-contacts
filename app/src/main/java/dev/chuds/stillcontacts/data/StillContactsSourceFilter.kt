package dev.chuds.stillcontacts.data

import android.provider.ContactsContract.RawContacts

internal data class RawContactSourceRow(
    val rawContactId: Long,
    val sourceId: String?,
    val accountName: String?,
    val accountType: String?,
)

internal data class RawContactsDeleteSelection(
    val selection: String,
    val selectionArgs: List<String>,
)

internal fun stillContactsRawContactIds(
    rows: Iterable<RawContactSourceRow>,
    account: AccountTarget,
): List<Long> = rows
    .filter { it.isStillContactsRaw(account) }
    .map { it.rawContactId }

internal fun preferredRawContactId(rows: Iterable<RawContactSourceRow>): Long? {
    val ordered = rows.sortedBy { it.rawContactId }
    return ordered.firstOrNull { it.sourceId == STILL_CONTACTS_SOURCE_ID }?.rawContactId
        ?: ordered.firstOrNull()?.rawContactId
}

internal fun stillContactsDeleteSelection(): RawContactsDeleteSelection =
    RawContactsDeleteSelection(
        selection = "${RawContacts.SOURCE_ID} = ?",
        selectionArgs = listOf(STILL_CONTACTS_SOURCE_ID),
    )

private fun RawContactSourceRow.isStillContactsRaw(account: AccountTarget): Boolean {
    if (sourceId != STILL_CONTACTS_SOURCE_ID) return false
    return when (account) {
        AccountTarget.PhoneOnly -> accountName == null && accountType == null
        is AccountTarget.Named -> accountName == account.name && accountType == account.type
    }
}
