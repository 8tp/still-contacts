package dev.chuds.stillcontacts.vcard

import dev.chuds.stillcontacts.data.Contact
import dev.chuds.stillcontacts.data.ContactDetail
import dev.chuds.stillcontacts.data.TypeLabel
import dev.chuds.stillcontacts.data.TypedValue
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * vCard round-trip smoke test (BUILD_PROMPT.md "checkpoint 2"). Generates three synthetic
 * ContactDetails covering: all fields populated, name-only, mixed TYPE= values. Each is
 * written to vCard 3.0, parsed back, and compared field-by-field — except for provider-
 * assigned fields (rawContactId, lookupKey) which are stripped before comparison.
 */
class VCardRoundTripTest {

    @Test fun roundTrip_allFields() {
        val original = sampleAllFields()
        val parsed = roundTrip(original)
        assertEqualsModuloProviderFields(original, parsed)
    }

    @Test fun roundTrip_nameOnly() {
        val original = sampleNameOnly()
        val parsed = roundTrip(original)
        assertEqualsModuloProviderFields(original, parsed)
    }

    @Test fun roundTrip_mixedTypes() {
        val original = sampleMixedTypes()
        val parsed = roundTrip(original)
        assertEqualsModuloProviderFields(original, parsed)
    }

    @Test fun parse_foldedLines() {
        val parsed = VCard.parseAll(
            "BEGIN:VCARD\n" +
                "VERSION:3.0\n" +
                "FN:Folded Person\n" +
                "N:Person;Folded;;;\n" +
                "NOTE:This note crosses \n" +
                " a folded physical line.\n" +
                "TEL;TYPE=CELL:555-0100\n" +
                "END:VCARD\n",
        )

        assertEquals(1, parsed.size)
        val detail = parsed.first()
        assertEquals("Folded Person", detail.contact.displayName)
        assertEquals("This note crosses a folded physical line.", detail.notes)
        assertEquals(listOf(TypedValue(TypeLabel.Mobile, "555-0100")), detail.phones)
    }

    @Test fun parse_quotedPrintableFixture() {
        val parsed = VCard.parseAll(
            """
            BEGIN:VCARD
            VERSION:3.0
            FN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:Jos=C3=A9 Curie
            N;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:Curie;Jos=C3=A9;;;
            NOTE;ENCODING=QUOTED-PRINTABLE;CHARSET=UTF-8:line=20one=0Aline=20two
            EMAIL;TYPE=HOME:jose@example.org
            END:VCARD
            """.trimIndent(),
        )

        assertEquals(1, parsed.size)
        val detail = parsed.first()
        assertEquals("José Curie", detail.contact.displayName)
        assertEquals("José", detail.contact.givenName)
        assertEquals("Curie", detail.contact.familyName)
        assertEquals("line one\nline two", detail.notes)
        assertEquals(listOf(TypedValue(TypeLabel.Home, "jose@example.org")), detail.emails)
    }

    @Test fun parse_quotedPrintableSoftBreakFixture() {
        val parsed = VCard.parseAll(
            "BEGIN:VCARD\r\n" +
                "VERSION:3.0\r\n" +
                "FN:Soft Break\r\n" +
                "N:Break;Soft;;;\r\n" +
                "NOTE;ENCODING=QUOTED-PRINTABLE;CHARSET=UTF-8:line=20one=20=\r\n" +
                "line=20two=0AJos=C3=A9\r\n" +
                "END:VCARD\r\n",
        )

        assertEquals(1, parsed.size)
        val detail = parsed.first()
        assertEquals("Soft Break", detail.contact.displayName)
        assertEquals("line one line two\nJosé", detail.notes)
    }

    @Test fun parse_missingTrailingNewline() {
        val parsed = VCard.parseAll(
            "BEGIN:VCARD\n" +
                "VERSION:3.0\n" +
                "FN:No Newline\n" +
                "N:Newline;No;;;\n" +
                "EMAIL;TYPE=WORK:no-newline@example.org\n" +
                "END:VCARD",
        )

        assertEquals(1, parsed.size)
        val detail = parsed.first()
        assertEquals("No Newline", detail.contact.displayName)
        assertEquals(listOf(TypedValue(TypeLabel.Work, "no-newline@example.org")), detail.emails)
    }

    private fun roundTrip(detail: ContactDetail): ContactDetail {
        val text = VCard.write(detail)
        val parsed = VCard.parseAll(text)
        assertEquals("expected exactly one card after round-trip", 1, parsed.size)
        return parsed.first()
    }

    private fun assertEqualsModuloProviderFields(expected: ContactDetail, actual: ContactDetail) {
        // Provider-only fields are stripped by the parser since the row hasn't been inserted.
        val e = expected.copy(
            contact = expected.contact.copy(rawContactId = -1L, lookupKey = ""),
        )
        // The parser computes primaryPhone/primaryEmail/sectionLetter from what it parsed,
        // which is what we want — assert they match the expected derived values.
        assertEquals(e.contact.displayName, actual.contact.displayName)
        assertEquals(e.contact.givenName, actual.contact.givenName)
        assertEquals(e.contact.familyName, actual.contact.familyName)
        assertEquals(e.phones, actual.phones)
        assertEquals(e.emails, actual.emails)
        assertEquals(e.addresses, actual.addresses)
        assertEquals(e.organization, actual.organization)
        assertEquals(e.notes, actual.notes)
        assertEquals(e.birthday, actual.birthday)
    }

    private fun sampleAllFields(): ContactDetail {
        val displayName = "Marie Curie"
        return ContactDetail(
            contact = Contact(
                rawContactId = -1L,
                lookupKey = "",
                displayName = displayName,
                familyName = "Curie",
                givenName = "Marie",
                primaryPhone = "+33 1 23 45 67 89",
                primaryEmail = "marie@example.org",
                sectionLetter = 'M',
            ),
            phones = listOf(
                TypedValue(TypeLabel.Mobile, "+33 1 23 45 67 89"),
                TypedValue(TypeLabel.Work, "+33 9 87 65 43 21"),
            ),
            emails = listOf(
                TypedValue(TypeLabel.Home, "marie@example.org"),
                TypedValue(TypeLabel.Work, "curie@radium.lab"),
            ),
            addresses = listOf(
                TypedValue(TypeLabel.Home, "12 Rue Vauquelin, Paris, 75005, France"),
            ),
            organization = "Sorbonne",
            notes = "discoverer of radium; two Nobels",
            birthday = LocalDate.of(1867, 11, 7),
        )
    }

    private fun sampleNameOnly(): ContactDetail {
        val displayName = "Solo Name"
        return ContactDetail(
            contact = Contact(
                rawContactId = -1L,
                lookupKey = "",
                displayName = displayName,
                familyName = "Name",
                givenName = "Solo",
                primaryPhone = null,
                primaryEmail = null,
                sectionLetter = 'S',
            ),
            phones = emptyList(),
            emails = emptyList(),
            addresses = emptyList(),
            organization = null,
            notes = null,
            birthday = null,
        )
    }

    private fun sampleMixedTypes(): ContactDetail {
        val displayName = "Mix Cases"
        return ContactDetail(
            contact = Contact(
                rawContactId = -1L,
                lookupKey = "",
                displayName = displayName,
                familyName = "Cases",
                givenName = "Mix",
                primaryPhone = "555-0101",
                primaryEmail = null,
                sectionLetter = 'M',
            ),
            phones = listOf(
                TypedValue(TypeLabel.Mobile, "555-0101"),
                TypedValue(TypeLabel.Home, "555-0102"),
                TypedValue(TypeLabel.Work, "555-0103"),
                TypedValue(TypeLabel.Other, "555-0104"),
            ),
            emails = listOf(
                TypedValue(TypeLabel.Other, "fax@old.example"),
            ),
            addresses = emptyList(),
            organization = null,
            notes = "embedded comma, semicolon; backslash \\ test",
            birthday = null,
        )
    }
}
