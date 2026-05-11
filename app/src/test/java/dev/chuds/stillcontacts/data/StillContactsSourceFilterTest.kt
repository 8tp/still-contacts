package dev.chuds.stillcontacts.data

import android.provider.ContactsContract.RawContacts
import org.junit.Assert.assertEquals
import org.junit.Test

class StillContactsSourceFilterTest {

    @Test fun filtersPhoneOnlyRowsByStillContactsSourceId() {
        val rows = listOf(
            RawContactSourceRow(
                rawContactId = 1L,
                sourceId = STILL_CONTACTS_SOURCE_ID,
                accountName = null,
                accountType = null,
            ),
            RawContactSourceRow(
                rawContactId = 2L,
                sourceId = "other-app",
                accountName = null,
                accountType = null,
            ),
            RawContactSourceRow(
                rawContactId = 3L,
                sourceId = STILL_CONTACTS_SOURCE_ID,
                accountName = "me@example.com",
                accountType = "com.example",
            ),
            RawContactSourceRow(
                rawContactId = 4L,
                sourceId = null,
                accountName = null,
                accountType = null,
            ),
        )

        assertEquals(
            listOf(1L),
            stillContactsRawContactIds(rows, AccountTarget.PhoneOnly),
        )
    }

    @Test fun preferredRawContactChoosesStillOwnedRawOverEarlierExternalRaw() {
        val rows = listOf(
            RawContactSourceRow(
                rawContactId = 1L,
                sourceId = "external",
                accountName = "sync@example.com",
                accountType = "com.example",
            ),
            RawContactSourceRow(
                rawContactId = 5L,
                sourceId = STILL_CONTACTS_SOURCE_ID,
                accountName = null,
                accountType = null,
            ),
        )

        assertEquals(5L, preferredRawContactId(rows))
    }

    @Test fun preferredRawContactFallsBackToFirstExternalRaw() {
        val rows = listOf(
            RawContactSourceRow(
                rawContactId = 7L,
                sourceId = "external",
                accountName = null,
                accountType = null,
            ),
            RawContactSourceRow(
                rawContactId = 9L,
                sourceId = null,
                accountName = null,
                accountType = null,
            ),
        )

        assertEquals(7L, preferredRawContactId(rows))
    }

    @Test fun filtersNamedAccountRowsByStillContactsSourceIdAndAccount() {
        val rows = listOf(
            RawContactSourceRow(
                rawContactId = 10L,
                sourceId = STILL_CONTACTS_SOURCE_ID,
                accountName = "me@example.com",
                accountType = "com.example",
            ),
            RawContactSourceRow(
                rawContactId = 11L,
                sourceId = STILL_CONTACTS_SOURCE_ID,
                accountName = "other@example.com",
                accountType = "com.example",
            ),
            RawContactSourceRow(
                rawContactId = 12L,
                sourceId = STILL_CONTACTS_SOURCE_ID,
                accountName = "me@example.com",
                accountType = "other.type",
            ),
            RawContactSourceRow(
                rawContactId = 13L,
                sourceId = "still-notes",
                accountName = "me@example.com",
                accountType = "com.example",
            ),
        )

        assertEquals(
            listOf(10L),
            stillContactsRawContactIds(
                rows,
                AccountTarget.Named(name = "me@example.com", type = "com.example"),
            ),
        )
    }

    @Test fun buildsPhoneOnlyDeleteSelectionForStillContactsSourceId() {
        val selection = stillContactsDeleteSelection(AccountTarget.PhoneOnly)

        assertEquals(
            "${RawContacts.SOURCE_ID} = ? AND " +
                "${RawContacts.ACCOUNT_NAME} IS NULL AND ${RawContacts.ACCOUNT_TYPE} IS NULL",
            selection.selection,
        )
        assertEquals(listOf(STILL_CONTACTS_SOURCE_ID), selection.selectionArgs)
    }

    @Test fun buildsNamedAccountDeleteSelectionForStillContactsSourceIdAndAccount() {
        val selection = stillContactsDeleteSelection(
            AccountTarget.Named(name = "me@example.com", type = "com.example"),
        )

        assertEquals(
            "${RawContacts.SOURCE_ID} = ? AND " +
                "${RawContacts.ACCOUNT_NAME} = ? AND ${RawContacts.ACCOUNT_TYPE} = ?",
            selection.selection,
        )
        assertEquals(
            listOf(STILL_CONTACTS_SOURCE_ID, "me@example.com", "com.example"),
            selection.selectionArgs,
        )
    }
}
