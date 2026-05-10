package dev.chuds.stillcontacts.vcard

/*
 * ContactDetail → vCard 3.0 text. Folds long lines at 75 octets per RFC 6350,
 * escapes \ , ; and \n inside text values. Emits CRLF line endings.
 *
 * vCard 3.0 (rather than 4.0) is the export target because every consumer (AOSP
 * Contacts, Fossify Contacts, Apple Contacts, every desktop client) imports 3.0
 * cleanly. The fields we touch (FN/N/TEL/EMAIL/ADR/ORG/NOTE/BDAY) have identical
 * shape in 4.0, so the parser accepts either on import.
 */

import dev.chuds.stillcontacts.data.ContactDetail
import dev.chuds.stillcontacts.data.TypeLabel
import dev.chuds.stillcontacts.data.TypedValue
import java.time.format.DateTimeFormatter

object VCardWriter {

    private const val LINE_FOLD_LIMIT = 75

    /** Write a single contact as a vCard 3.0 block (with trailing CRLF). */
    fun write(detail: ContactDetail): String {
        val out = StringBuilder()
        writeInto(out, detail)
        return out.toString()
    }

    /** Write many contacts as one byte stream, blocks back-to-back. */
    fun writeAll(details: List<ContactDetail>): String {
        val out = StringBuilder()
        for (detail in details) writeInto(out, detail)
        return out.toString()
    }

    private fun writeInto(out: StringBuilder, detail: ContactDetail) {
        appendLine(out, "BEGIN:VCARD")
        appendLine(out, "VERSION:3.0")

        val fn = detail.contact.displayName.ifBlank {
            listOfNotNull(detail.contact.givenName, detail.contact.familyName).joinToString(" ").ifBlank { "Unnamed" }
        }
        appendLine(out, "FN:${escapeText(fn)}")
        appendLine(out, "N:${escapeStructured(listOf(detail.contact.familyName.orEmpty(), detail.contact.givenName.orEmpty(), "", "", ""))}")

        detail.phones.forEach { tv ->
            appendLine(out, "TEL;${typeParam(tv)}:${escapeText(tv.value)}")
        }
        detail.emails.forEach { tv ->
            appendLine(out, "EMAIL;${typeParam(tv)}:${escapeText(tv.value)}")
        }
        detail.addresses.forEach { tv ->
            // Encode the whole address into the street segment of an ADR structured value.
            // We have no per-component breakdown when the address came in as FORMATTED_ADDRESS;
            // round-tripping through this writer + the parser preserves the formatted text.
            appendLine(out, "ADR;${typeParam(tv)}:${escapeStructured(listOf("", "", tv.value, "", "", "", ""))}")
        }
        detail.organization?.takeIf { it.isNotBlank() }?.let {
            appendLine(out, "ORG:${escapeText(it)}")
        }
        detail.notes?.takeIf { it.isNotBlank() }?.let {
            appendLine(out, "NOTE:${escapeText(it)}")
        }
        detail.birthday?.let {
            appendLine(out, "BDAY:${it.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))}")
        }

        appendLine(out, "END:VCARD")
    }

    private fun typeParam(tv: TypedValue): String = when (tv.type) {
        TypeLabel.Mobile -> "TYPE=CELL"
        TypeLabel.Home -> "TYPE=HOME"
        TypeLabel.Work -> "TYPE=WORK"
        TypeLabel.Other -> "TYPE=VOICE"
    }

    private fun escapeStructured(parts: List<String>): String =
        parts.joinToString(";") { escapeText(it) }

    /** Append a logical line, folding at 75 octets, terminated by CRLF. */
    private fun appendLine(out: StringBuilder, line: String) {
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (bytes.size <= LINE_FOLD_LIMIT) {
            out.append(line)
            out.append("\r\n")
            return
        }
        // Fold by octet count, taking care not to split a multi-byte codepoint mid-sequence.
        var pos = 0
        var first = true
        while (pos < bytes.size) {
            var endByte = (pos + if (first) LINE_FOLD_LIMIT else LINE_FOLD_LIMIT - 1).coerceAtMost(bytes.size)
            // Walk back so we don't slice a continuation byte.
            while (endByte < bytes.size && (bytes[endByte].toInt() and 0xC0) == 0x80) endByte--
            val chunk = String(bytes, pos, endByte - pos, Charsets.UTF_8)
            if (!first) out.append(' ')
            out.append(chunk)
            out.append("\r\n")
            pos = endByte
            first = false
        }
    }
}
