package com.example.venus.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object VenusUtils {
    fun nombreArchivo(context: Context, uri: Uri): String {
        var nombre = "Video local"
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val indice = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (indice >= 0) {
                        nombre = cursor.getString(indice) ?: nombre
                    }
                }
            }
        } catch (_: Exception) {}
        return nombre
    }
}
