package dev.chuds.stillcontacts.data

internal data class RawContactSourceRow(
    val rawContactId: Long,
    val sourceId: String?,
    val accountName: String?,
    val accountType: String?,
)

internal fun stillContactsRawContactIds(
    rows: Iterable<RawContactSourceRow>,
    account: AccountTarget,
): List<Long> = rows
    .filter { it.isStillContactsRaw(account) }
    .map { it.rawContactId }

private fun RawContactSourceRow.isStillContactsRaw(account: AccountTarget): Boolean {
    if (sourceId != STILL_CONTACTS_SOURCE_ID) return false
    return when (account) {
        AccountTarget.PhoneOnly -> accountName == null && accountType == null
        is AccountTarget.Named -> accountName == account.name && accountType == account.type
    }
}
