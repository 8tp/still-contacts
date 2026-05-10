package dev.chuds.stillcontacts.data

import android.provider.ContactsContract.CommonDataKinds
import java.time.LocalDate

/**
 * Typed projections of the system ContactsContract provider that the Compose layer
 * consumes. The Compose layer never touches ContentResolver directly — every cursor
 * walk and applyBatch lives behind ContactsRepository.
 *
 * The four-value TypeLabel enum is deliberately small (spec §5.2). Real contacts in
 * the provider have dozens of TYPE_* ints (TYPE_FAX_HOME, TYPE_PAGER, TYPE_ASSISTANT,
 * etc.). On import / read we collapse them to the nearest of the four; the original
 * int is not preserved across a still-contacts edit. This is documented in the README
 * "what it refuses" table.
 */

data class Contact(
    val rawContactId: Long,
    val lookupKey: String,
    val displayName: String,
    val familyName: String?,
    val givenName: String?,
    val primaryPhone: String?,
    val primaryEmail: String?,
    val sectionLetter: Char,
)

data class ContactDetail(
    val contact: Contact,
    val phones: List<TypedValue>,
    val emails: List<TypedValue>,
    val addresses: List<TypedValue>,
    val organization: String?,
    val notes: String?,
    val birthday: LocalDate?,
)

data class TypedValue(
    val type: TypeLabel,
    val value: String,
)

enum class TypeLabel(
    val vCardLabel: String,
    val phoneType: Int,
    val emailType: Int,
    val addressType: Int,
) {
    Mobile(
        vCardLabel = "CELL",
        phoneType = CommonDataKinds.Phone.TYPE_MOBILE,
        emailType = CommonDataKinds.Email.TYPE_MOBILE,
        addressType = CommonDataKinds.StructuredPostal.TYPE_OTHER,
    ),
    Home(
        vCardLabel = "HOME",
        phoneType = CommonDataKinds.Phone.TYPE_HOME,
        emailType = CommonDataKinds.Email.TYPE_HOME,
        addressType = CommonDataKinds.StructuredPostal.TYPE_HOME,
    ),
    Work(
        vCardLabel = "WORK",
        phoneType = CommonDataKinds.Phone.TYPE_WORK,
        emailType = CommonDataKinds.Email.TYPE_WORK,
        addressType = CommonDataKinds.StructuredPostal.TYPE_WORK,
    ),
    Other(
        vCardLabel = "VOICE",
        phoneType = CommonDataKinds.Phone.TYPE_OTHER,
        emailType = CommonDataKinds.Email.TYPE_OTHER,
        addressType = CommonDataKinds.StructuredPostal.TYPE_OTHER,
    );

    fun next(): TypeLabel = when (this) {
        Mobile -> Home
        Home -> Work
        Work -> Other
        Other -> Mobile
    }

    val label: String
        get() = name.lowercase()

    companion object {
        fun fromPhoneType(type: Int): TypeLabel = when (type) {
            CommonDataKinds.Phone.TYPE_MOBILE -> Mobile
            CommonDataKinds.Phone.TYPE_HOME -> Home
            CommonDataKinds.Phone.TYPE_WORK -> Work
            CommonDataKinds.Phone.TYPE_FAX_HOME -> Home
            CommonDataKinds.Phone.TYPE_FAX_WORK -> Work
            else -> Other
        }

        fun fromEmailType(type: Int): TypeLabel = when (type) {
            CommonDataKinds.Email.TYPE_MOBILE -> Mobile
            CommonDataKinds.Email.TYPE_HOME -> Home
            CommonDataKinds.Email.TYPE_WORK -> Work
            else -> Other
        }

        fun fromAddressType(type: Int): TypeLabel = when (type) {
            CommonDataKinds.StructuredPostal.TYPE_HOME -> Home
            CommonDataKinds.StructuredPostal.TYPE_WORK -> Work
            else -> Other
        }

        /** Map a free-form vCard TYPE= token to one of the four. Unknown collapses to Other. */
        fun fromVCardToken(token: String): TypeLabel {
            val t = token.trim().uppercase()
            return when (t) {
                "CELL", "MOBILE", "MSG" -> Mobile
                "HOME" -> Home
                "WORK" -> Work
                else -> Other
            }
        }
    }
}

/** Where a new RawContacts row is written. */
sealed interface AccountTarget {
    /** ACCOUNT_NAME = null, ACCOUNT_TYPE = null — the local "phone-only" account. */
    data object PhoneOnly : AccountTarget

    /** A device account already known to AccountManager and to ContactsContract. */
    data class Named(val name: String, val type: String) : AccountTarget
}
