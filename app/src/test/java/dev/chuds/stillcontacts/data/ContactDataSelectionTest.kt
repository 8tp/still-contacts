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
}
