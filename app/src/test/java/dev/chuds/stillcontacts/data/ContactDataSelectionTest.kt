package dev.chuds.stillcontacts.data

import android.provider.ContactsContract.Data
import android.provider.ContactsContract.CommonDataKinds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun organizationInsertPreservesTitleDepartmentAndJobDescription() {
        val values = organizationInsertValues(
            company = "Sorbonne",
            preserved = OrganizationSubfields(
                title = "Professor",
                department = "Physics",
                jobDescription = "Research lead",
                type = CommonDataKinds.Organization.TYPE_OTHER,
            ),
        )

        assertEquals("Sorbonne", values[CommonDataKinds.Organization.COMPANY])
        assertEquals("Professor", values[CommonDataKinds.Organization.TITLE])
        assertEquals("Physics", values[CommonDataKinds.Organization.DEPARTMENT])
        assertEquals("Research lead", values[CommonDataKinds.Organization.JOB_DESCRIPTION])
        assertEquals(
            CommonDataKinds.Organization.TYPE_OTHER,
            values[CommonDataKinds.Organization.TYPE],
        )
    }

    @Test fun organizationInsertDefaultsToWorkTypeWhenNoTypeIsPreserved() {
        val values = organizationInsertValues(
            company = "Acme",
            preserved = OrganizationSubfields.Empty,
        )

        assertEquals("Acme", values[CommonDataKinds.Organization.COMPANY])
        assertEquals(
            CommonDataKinds.Organization.TYPE_WORK,
            values[CommonDataKinds.Organization.TYPE],
        )
        assertFalse(values.containsKey(CommonDataKinds.Organization.TITLE))
        assertFalse(values.containsKey(CommonDataKinds.Organization.DEPARTMENT))
        assertFalse(values.containsKey(CommonDataKinds.Organization.JOB_DESCRIPTION))
    }

    @Test fun organizationInsertDropsBlankPreservedSubfields() {
        val values = organizationInsertValues(
            company = "Acme",
            preserved = OrganizationSubfields(
                title = "  ",
                department = "",
                jobDescription = null,
                type = null,
            ),
        )

        assertFalse(values.containsKey(CommonDataKinds.Organization.TITLE))
        assertFalse(values.containsKey(CommonDataKinds.Organization.DEPARTMENT))
        assertFalse(values.containsKey(CommonDataKinds.Organization.JOB_DESCRIPTION))
    }
}
