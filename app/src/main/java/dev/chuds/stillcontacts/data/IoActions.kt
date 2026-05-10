package dev.chuds.stillcontacts.data

import android.content.Context
import android.net.Uri
import android.widget.Toast
import dev.chuds.stillcontacts.vcard.VCard
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SAF helpers — the Compose layer never touches ContentResolver directly. Mirrors
 * still-notes' IoActions: read URIs as text, hand bytes to the repository.
 */

suspend fun importVCardsFromUris(
    context: Context,
    uris: List<Uri>,
    repository: ContactsRepository,
    account: AccountTarget,
): Int = withContext(Dispatchers.IO) {
    val parsed = mutableListOf<ContactDetail>()
    uris.forEach { uri ->
        val text = readTextFromUri(context, uri) ?: return@forEach
        parsed += VCard.parseAll(text)
    }
    if (parsed.isEmpty()) return@withContext 0
    repository.importBatch(parsed, account)
}

suspend fun importVCardFromSingleUri(
    context: Context,
    uri: Uri,
    repository: ContactsRepository,
    account: AccountTarget,
): Int = importVCardsFromUris(context, listOf(uri), repository, account)

suspend fun writeSingleVCardToUri(
    context: Context,
    uri: Uri,
    detail: ContactDetail,
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(VCard.write(detail).toByteArray(Charsets.UTF_8))
        } ?: return@runCatching false
        true
    }.getOrElse {
        toastOnMain(context, "export failed")
        false
    }
}

suspend fun writeAllVCardsToUri(
    context: Context,
    uri: Uri,
    repository: ContactsRepository,
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val all = repository.loadAllForExport()
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(VCard.writeAll(all).toByteArray(Charsets.UTF_8))
        } ?: return@runCatching false
        true
    }.getOrElse {
        toastOnMain(context, "bulk export failed")
        false
    }
}

private fun readTextFromUri(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }
}.getOrNull()

private fun toastOnMain(context: Context, message: String) {
    android.os.Handler(context.mainLooper).post {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

/** Build a default filename for a single-contact export, ASCII-only and lowercase. */
fun safeContactFilename(displayName: String): String {
    val cleaned = displayName.lowercase()
        .replace(Regex("\\s+"), "-")
        .replace(Regex("[^a-z0-9._\\-]"), "")
        .trim('-')
    val base = if (cleaned.isBlank()) "contact" else cleaned
    return "$base.vcf"
}
