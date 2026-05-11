package dev.chuds.stillcontacts.data

import android.provider.ContactsContract.Data

internal data class ContactDataSelection(
    val selection: String,
    val selectionArgs: List<String>,
)

internal fun rawContactDataSelection(rawContactId: Long): ContactDataSelection =
    ContactDataSelection(
        selection = "${Data.RAW_CONTACT_ID} = ?",
        selectionArgs = listOf(rawContactId.toString()),
    )

internal fun aggregateContactDataSelection(contactId: Long): ContactDataSelection =
    ContactDataSelection(
        selection = "${Data.CONTACT_ID} = ?",
        selectionArgs = listOf(contactId.toString()),
    )
