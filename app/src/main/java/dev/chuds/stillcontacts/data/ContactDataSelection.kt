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

internal data class OrganizationSubfields(
    val title: String?,
    val department: String?,
    val jobDescription: String?,
    val type: Int? = null,
) {
    fun isEmpty(): Boolean =
        title.isNullOrBlank() && department.isNullOrBlank() && jobDescription.isNullOrBlank()

    companion object {
        val Empty = OrganizationSubfields(null, null, null, null)
    }
}

/**
 * Merge the new company string with any preserved subfields read from the existing org row.
 * Returns a column→value map; blank/null values are dropped so the provider treats them as
 * absent. TYPE defaults to TYPE_WORK when no preserved type is known.
 */
internal fun organizationInsertValues(
    company: String,
    preserved: OrganizationSubfields,
): Map<String, Any?> {
    val values = LinkedHashMap<String, Any?>()
    values[CommonDataKinds.Organization.COMPANY] = company
    values[CommonDataKinds.Organization.TYPE] =
        preserved.type ?: CommonDataKinds.Organization.TYPE_WORK
    preserved.title?.takeIf { it.isNotBlank() }?.let {
        values[CommonDataKinds.Organization.TITLE] = it
    }
    preserved.department?.takeIf { it.isNotBlank() }?.let {
        values[CommonDataKinds.Organization.DEPARTMENT] = it
    }
    preserved.jobDescription?.takeIf { it.isNotBlank() }?.let {
        values[CommonDataKinds.Organization.JOB_DESCRIPTION] = it
    }
    return values
}

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
