package dev.chuds.stillcontacts.vcard

/*
 * Hand-rolled vCard tokenizer. Per spec §5.4 the parser must round-trip its own
 * output exactly, which is the only correctness floor we hold ourselves to.
 *
 * Why hand-rolled and not ez-vcard or similar:
 *   The pact (STILL.md) refuses third-party parsers. A vCard subset that covers
 *   FN/N/TEL/EMAIL/ADR/ORG/NOTE/BDAY is small enough for one person to maintain.
 *
 * Why vCard 3.0 on write (4.0 accepted on import):
 *   3.0 is the most widely interoperable target — every consumer (AOSP Contacts,
 *   Fossify Contacts, Apple Contacts, every desktop client) imports it without
 *   surprises. 4.0 is mostly the same shape; we accept it on import via the same
 *   property names since the field set we touch is identical.
 *
 * Line unfolding (RFC 6350 §3.2): a line that begins with whitespace is a
 * continuation of the previous line. This must happen before parameter-splitting
 * because folded lines may split parameter values.
 */

internal object VCardLexer {

    /**
     * Unfold raw vCard text into logical lines. Strips the leading whitespace from
     * every continuation line, then concatenates onto the previous logical line.
     * Handles \r\n, \n, and \r line endings; emits \n-free logical lines.
     */
    fun unfold(text: String): List<String> {
        // Normalize CRLF / CR to \n first.
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val rawLines = normalized.split('\n')
        val logical = mutableListOf<StringBuilder>()
        for (line in rawLines) {
            if (line.isEmpty()) continue
            val first = line[0]
            if ((first == ' ' || first == '\t') && logical.isNotEmpty()) {
                // Continuation: drop the single leading whitespace char per RFC 6350.
                logical.last().append(line.substring(1))
            } else {
                logical += StringBuilder(line)
            }
        }
        return logical.map { it.toString() }
    }
}
