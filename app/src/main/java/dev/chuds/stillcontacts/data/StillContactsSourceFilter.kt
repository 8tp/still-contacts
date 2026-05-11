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

internal fun stillContactsDeleteSelection(account: AccountTarget): RawContactsDeleteSelection =
    when (account) {
        AccountTarget.PhoneOnly -> RawContactsDeleteSelection(
            selection = "${RawContacts.SOURCE_ID} = ? AND " +
                "${RawContacts.ACCOUNT_NAME} IS NULL AND ${RawContacts.ACCOUNT_TYPE} IS NULL",
            selectionArgs = listOf(STILL_CONTACTS_SOURCE_ID),
        )
        is AccountTarget.Named -> RawContactsDeleteSelection(
            selection = "${RawContacts.SOURCE_ID} = ? AND " +
                "${RawContacts.ACCOUNT_NAME} = ? AND ${RawContacts.ACCOUNT_TYPE} = ?",
            selectionArgs = listOf(STILL_CONTACTS_SOURCE_ID, account.name, account.type),
        )
    }

private fun RawContactSourceRow.isStillContactsRaw(account: AccountTarget): Boolean {
    if (sourceId != STILL_CONTACTS_SOURCE_ID) return false
    return when (account) {
        AccountTarget.PhoneOnly -> accountName == null && accountType == null
        is AccountTarget.Named -> accountName == account.name && accountType == account.type
    }
}
