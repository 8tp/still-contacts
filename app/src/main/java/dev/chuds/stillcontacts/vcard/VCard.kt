package dev.chuds.stillcontacts.vcard

import dev.chuds.stillcontacts.data.ContactDetail

/**
 * Small public façade wrapping the parser/writer. The rest of the app touches
 * [VCard] only — never the internal Lexer / Parser / Types / Writer split.
 */
object VCard {
    fun parseAll(text: String): List<ContactDetail> {
        return VCardParser.parse(text).mapNotNull { VCardTypes.toDetail(it) }
    }

    fun writeAll(details: List<ContactDetail>): String = VCardWriter.writeAll(details)

    fun write(detail: ContactDetail): String = VCardWriter.write(detail)
}
