package dev.chuds.stillcontacts.vcard

import dev.chuds.stillcontacts.data.Contact
import dev.chuds.stillcontacts.data.ContactDetail
import dev.chuds.stillcontacts.data.TypeLabel
import dev.chuds.stillcontacts.data.TypedValue
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Maps raw vCard property triples into typed [ContactDetail]s. Tolerates missing
 * fields with sensible defaults; collapses unknown TYPE= values to TypeLabel.Other;
 * silently drops PHOTO, CATEGORIES, and any other unsupported property (see spec §4).
 *
 * Unknown TYPE= values: anything not in {CELL, MOBILE, MSG, HOME, WORK} → Other.
 * Malformed BDAY: dropped, the rest of the contact survives (spec §13).
 */
internal object VCardTypes {

    fun toDetail(raw: RawVCard): ContactDetail? {
        var fn: String? = null
        var givenName: String? = null
        var familyName: String? = null
        val phones = mutableListOf<TypedValue>()
        val emails = mutableListOf<TypedValue>()
        val addresses = mutableListOf<TypedValue>()
        var organization: String? = null
        var notes: String? = null
        var birthday: LocalDate? = null

        for (prop in raw.properties) {
            when (prop.name.uppercase()) {
                "FN" -> fn = unescapeText(prop.value)
                "N" -> {
                    // vCard N is structured: family;given;additional;prefix;suffix
                    val parts = splitStructured(prop.value)
                    familyName = parts.getOrNull(0)?.takeIf { it.isNotBlank() }?.let(::unescapeText)
                    givenName = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let(::unescapeText)
                }
                "TEL" -> {
                    val number = unescapeText(prop.value).trim()
                    if (number.isNotEmpty()) phones += TypedValue(typeFromTokens(prop.typeTokens()), number)
                }
                "EMAIL" -> {
                    val addr = unescapeText(prop.value).trim()
                    if (addr.isNotEmpty()) emails += TypedValue(typeFromTokens(prop.typeTokens()), addr)
                }
                "ADR" -> {
                    // ADR is structured: pobox;extaddr;street;locality;region;postal;country
                    val parts = splitStructured(prop.value).map { unescapeText(it) }
                    val joined = listOf(
                        parts.getOrNull(2),  // street
                        parts.getOrNull(3),  // locality
                        parts.getOrNull(4),  // region
                        parts.getOrNull(5),  // postal
                        parts.getOrNull(6),  // country
                    ).filterNot { it.isNullOrBlank() }.joinToString(", ")
                    if (joined.isNotEmpty()) addresses += TypedValue(typeFromTokens(prop.typeTokens()), joined)
                }
                "ORG" -> {
                    val parts = splitStructured(prop.value).map { unescapeText(it) }
                    organization = parts.firstOrNull()?.takeIf { it.isNotBlank() }
                }
                "NOTE" -> notes = unescapeText(prop.value)
                "BDAY" -> birthday = parseBdayValue(prop.value)
                // PHOTO, CATEGORIES, X-… and everything else: silently drop.
            }
        }

        val displayName = fn?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(givenName, familyName).joinToString(" ").ifBlank { "" }

        // Reject empty cards entirely — at least one of name, phone, email is required by spec §6.3.
        if (displayName.isBlank() && phones.isEmpty() && emails.isEmpty()) return null

        val contact = Contact(
            rawContactId = -1L,
            lookupKey = "",
            displayName = displayName,
            familyName = familyName,
            givenName = givenName,
            primaryPhone = phones.firstOrNull()?.value,
            primaryEmail = emails.firstOrNull()?.value,
            sectionLetter = displayName.trimStart().firstOrNull()
                ?.let { if (it.isLetter()) it.uppercaseChar() else '#' } ?: '#',
        )

        return ContactDetail(
            contact = contact,
            phones = phones,
            emails = emails,
            addresses = addresses,
            organization = organization,
            notes = notes,
            birthday = birthday,
        )
    }

    private fun typeFromTokens(tokens: List<String>): TypeLabel {
        if (tokens.isEmpty()) return TypeLabel.Other
        // Pick the first recognized token. INTERNET / VOICE / PREF are noise — skip them.
        for (tok in tokens) {
            val t = tok.trim().uppercase()
            if (t == "INTERNET" || t == "PREF" || t == "VOICE") continue
            return TypeLabel.fromVCardToken(t)
        }
        return TypeLabel.Other
    }

    private fun parseBdayValue(value: String): LocalDate? {
        val raw = value.trim()
        if (raw.isEmpty()) return null
        return runCatching { LocalDate.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }.getOrNull()
            ?: runCatching { LocalDate.parse(raw, DateTimeFormatter.ofPattern("yyyyMMdd")) }.getOrNull()
            ?: runCatching {
                // vCard 4.0 allows --MMDD for an unknown year.
                if (raw.startsWith("--") && raw.length >= 6) {
                    LocalDate.of(1604, raw.substring(2, 4).toInt(), raw.substring(4, 6).toInt())
                } else null
            }.getOrNull()
    }

    /** Split a vCard structured value on unescaped semicolons. */
    private fun splitStructured(value: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                cur.append(c)
                cur.append(value[i + 1])
                i += 2
                continue
            }
            if (c == ';') {
                out += cur.toString()
                cur.clear()
            } else {
                cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out
    }
}
