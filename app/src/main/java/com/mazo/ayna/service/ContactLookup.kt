package com.mazo.ayna.service

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract

/** Resolve a contact's display name to a phone number (for voice WhatsApp/SMS). */
object ContactLookup {

    fun findPhoneNumber(context: Context, name: String): String? {
        val resolver: ContentResolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%$name%")
        var cursor: Cursor? = null
        return try {
            cursor = resolver.query(uri, projection, selection, args, null)
            if (cursor != null && cursor.moveToFirst()) {
                cursor.getString(0)?.filter { it.isDigit() || it == '+' }
            } else {
                // Maybe the user spoke digits already.
                name.filter { it.isDigit() || it == '+' }.takeIf { it.length >= 10 }
            }
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }
}
