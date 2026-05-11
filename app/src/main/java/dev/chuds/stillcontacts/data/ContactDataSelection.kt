package dev.chuds.stillcontacts.data

import android.provider.ContactsContract.CommonDataKinds
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

internal fun supportedDataReplaceSelection(rawContactId: Long): ContactDataSelection {
    val replaceMimetypes = listOf(
        CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
        CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
        CommonDataKinds.Email.CONTENT_ITEM_TYPE,
        CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
        CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
        CommonDataKinds.Note.CONTENT_ITEM_TYPE,
    )
    val placeholders = replaceMimetypes.joinToString(", ") { "?" }
    return ContactDataSelection(
        selection = "${Data.RAW_CONTACT_ID} = ? AND " +
            "(${Data.MIMETYPE} IN ($placeholders) OR " +
            "(${Data.MIMETYPE} = ? AND ${CommonDataKinds.Event.TYPE} = ?))",
        selectionArgs = listOf(rawContactId.toString()) +
            replaceMimetypes +
            CommonDataKinds.Event.CONTENT_ITEM_TYPE +
            CommonDataKinds.Event.TYPE_BIRTHDAY.toString(),
    )
}
