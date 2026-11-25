package io.github.kurtking514_byte.capricorn.capri

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.core.content.ContextCompat

/** One phone number of one contact. [type] is "MOBILE", "WORK", "HOME"… */
data class ContactNumber(val name: String, val number: String, val type: String)

/**
 * 3.13: "call mom". Finds contacts by name (needs Contacts access, asked once) and opens the
 * phone app with the number filled in: you press call yourself, Capri never calls on her own.
 */
object Contacts {
    fun allowed(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Numbers whose contact name matches [query]: exact name first, then starts-with, then contains; starred first. */
    fun find(context: Context, query: String, all: Boolean = false): List<ContactNumber> {
        if (!allowed(context)) return emptyList()
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        data class Hit(val c: ContactNumber, val rank: Int)
        val hits = mutableListOf<Hit>()
        val seen = HashSet<String>()
        try {
            context.contentResolver.query(
                Phone.CONTENT_URI,
                arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE, Phone.LABEL, Phone.STARRED),
                "${Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$q%"), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val number = c.getString(1) ?: continue
                    val key = name.lowercase() + "|" + number.filter { it.isDigit() }.takeLast(9)
                    if (!seen.add(key)) continue
                    val type = Phone.getTypeLabel(context.resources, c.getInt(2), c.getString(3)).toString().uppercase()
                    val n = name.lowercase()
                    val words = n.split(" ")
                    var rank = when {
                        n == q -> 0
                        words.any { it == q } -> 1
                        n.startsWith(q) || words.any { it.startsWith(q) } -> 2
                        else -> 3
                    } * 2
                    if (c.getInt(4) == 0) rank += 1                    // starred contacts first within a rank
                    hits += Hit(ContactNumber(name, number, type), rank)
                }
            }
        } catch (e: Exception) { return emptyList() }
        if (all) return hits.sortedBy { it.rank }.map { it.c }        // 3.14 live search: every match, best first
        val best = hits.minOfOrNull { it.rank / 2 } ?: return emptyList()
        return hits.filter { it.rank / 2 == best }.sortedBy { it.rank }.map { it.c }
    }

    /** Opens the phone app with [c]'s number typed in. */
    fun dial(context: Context, c: ContactNumber): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(c.number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) { false }
}
