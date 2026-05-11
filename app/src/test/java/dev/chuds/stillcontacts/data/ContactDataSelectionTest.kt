package dev.chuds.stillcontacts.data

import android.provider.ContactsContract.Data
import android.provider.ContactsContract.CommonDataKinds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun buildsSupportedDataReplaceSelectionForStillEditableRowsOnly() {
        val selection = supportedDataReplaceSelection(rawContactId = 42L)

        assertTrue(selection.selection.startsWith("${Data.RAW_CONTACT_ID} = ? AND"))
        assertTrue(selection.selection.contains("${Data.MIMETYPE} IN"))
        assertTrue(selection.selection.contains("${CommonDataKinds.Event.TYPE} = ?"))
        assertEquals("42", selection.selectionArgs.first())
        assertTrue(selection.selectionArgs.contains(CommonDataKinds.Phone.CONTENT_ITEM_TYPE))
        assertTrue(selection.selectionArgs.contains(CommonDataKinds.Event.CONTENT_ITEM_TYPE))
        assertTrue(selection.selectionArgs.contains(CommonDataKinds.Event.TYPE_BIRTHDAY.toString()))
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
