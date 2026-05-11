package dev.chuds.stillcontacts.data

import android.provider.ContactsContract.Data
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactDataSelectionTest {

    @Test fun buildsRawContactScopedDataSelection() {
        val selection = rawContactDataSelection(rawContactId = 42L)

        assertEquals("${Data.RAW_CONTACT_ID} = ?", selection.selection)
        assertEquals(listOf("42"), selection.selectionArgs)
    }

    @Test fun buildsAggregateContactScopedDataSelection() {
        val selection = aggregateContactDataSelection(contactId = 99L)

        assertEquals("${Data.CONTACT_ID} = ?", selection.selection)
        assertEquals(listOf("99"), selection.selectionArgs)
    }

    @Test fun resolvesDisplayNameFromProviderFallbackWhenDataRowsHaveNoName() {
        assertEquals(
            "Phone Only",
            resolveContactDisplayName(
                dataDisplayName = "",
                fallbackDisplayName = "Phone Only",
            ),
        )
    }

    @Test fun keepsDataRowDisplayNameBeforeProviderFallback() {
        assertEquals(
            "Structured Name",
            resolveContactDisplayName(
                dataDisplayName = "Structured Name",
                fallbackDisplayName = "Provider Name",
            ),
        )
    }
}
