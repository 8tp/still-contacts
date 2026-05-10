package dev.chuds.stillcontacts.vcard

/*
 * Block-recognizer: walks a list of logical lines and groups them into RawVCard
 * blocks bounded by BEGIN:VCARD / END:VCARD. Each line is split into a
 * (name, params, value) triple; param values are unescaped and TYPE= tokens are
 * collected as a list (vCard 3.0 allows TYPE=A;TYPE=B or TYPE=A,B).
 */

internal data class RawProperty(
    val name: String,
    val params: Map<String, List<String>>,
    val value: String,
) {
    fun typeTokens(): List<String> = params["TYPE"].orEmpty()
}

internal data class RawVCard(val properties: List<RawProperty>)

internal object VCardParser {

    fun parse(text: String): List<RawVCard> {
        val logicalLines = VCardLexer.unfold(text)
        val cards = mutableListOf<RawVCard>()
        var current: MutableList<RawProperty>? = null
        for (line in logicalLines) {
            val prop = parseLine(line) ?: continue
            when (prop.name.uppercase()) {
                "BEGIN" -> if (prop.value.equals("VCARD", ignoreCase = true)) {
                    current = mutableListOf()
                }
                "END" -> if (prop.value.equals("VCARD", ignoreCase = true) && current != null) {
                    cards += RawVCard(current)
                    current = null
                }
                else -> current?.add(prop)
            }
        }
        return cards
    }

    /**
     * Parse a single logical line of the form:
     *   NAME[;PARAM=VALUE[;PARAM=VALUE]…]:VALUE
     * Returns null for blank lines or lines with no colon (malformed → skipped).
     */
    private fun parseLine(line: String): RawProperty? {
        if (line.isBlank()) return null
        val colon = findUnescapedColon(line) ?: return null
        val left = line.substring(0, colon)
        val value = line.substring(colon + 1)

        val parts = splitOnSemicolonOutsideQuotes(left)
        if (parts.isEmpty()) return null
        // The first segment may itself be of the form `GROUP.NAME` per RFC; treat the
        // dot-suffix as the name if present and drop the group label.
        val first = parts[0]
        val name = first.substringAfterLast('.', first)

        val params = mutableMapOf<String, MutableList<String>>()
        for (i in 1 until parts.size) {
            val seg = parts[i]
            val eq = seg.indexOf('=')
            if (eq < 0) {
                // bare-token form: TYPE=…  default vCard 2.1 also supported "HOME" alone.
                params.getOrPut("TYPE") { mutableListOf() } += seg.trim()
            } else {
                val key = seg.substring(0, eq).trim().uppercase()
                val raw = seg.substring(eq + 1).trim().trim('"')
                // Comma-separated list collapses to multi values.
                raw.split(',').forEach { tok ->
                    val t = tok.trim().trim('"')
                    if (t.isNotEmpty()) params.getOrPut(key) { mutableListOf() } += t
                }
            }
        }

        return RawProperty(name = name, params = params, value = value)
    }

    private fun findUnescapedColon(line: String): Int? {
        var i = 0
        var inQuotes = false
        while (i < line.length) {
            val c = line[i]
            when (c) {
                '\\' -> i++ // skip the next char
                '"' -> inQuotes = !inQuotes
                ':' -> if (!inQuotes) return i
            }
            i++
        }
        return null
    }

    private fun splitOnSemicolonOutsideQuotes(s: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < s.length -> {
                    current.append(c)
                    current.append(s[i + 1])
                    i += 2
                    continue
                }
                c == '"' -> {
                    inQuotes = !inQuotes
                    current.append(c)
                }
                c == ';' && !inQuotes -> {
                    out += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        out += current.toString()
        return out
    }
}

/** Unescape a vCard text-value: \\ \, \; \n / \N → \ , ; newline. */
internal fun unescapeText(value: String): String {
    val sb = StringBuilder(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '\\' && i + 1 < value.length) {
            when (val next = value[i + 1]) {
                'n', 'N' -> sb.append('\n')
                ',', ';', '\\' -> sb.append(next)
                else -> {
                    sb.append(c)
                    sb.append(next)
                }
            }
            i += 2
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}

/** Escape a text-value for vCard: \ , ; and newline. */
internal fun escapeText(value: String): String {
    val sb = StringBuilder(value.length + 8)
    for (c in value) {
        when (c) {
            '\\' -> sb.append("\\\\")
            ',' -> sb.append("\\,")
            ';' -> sb.append("\\;")
            '\n' -> sb.append("\\n")
            '\r' -> { /* drop bare CR */ }
            else -> sb.append(c)
        }
    }
    return sb.toString()
}
