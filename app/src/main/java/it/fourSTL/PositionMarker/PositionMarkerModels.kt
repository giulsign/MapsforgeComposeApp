package it.fourSTL.PositionMarker

import android.content.Context
import org.json.JSONObject
import kotlin.collections.remove

/**
 * Rappresenta un elemento generico (categoria, sottocategoria, metadato)
 * letto dai file JSON presenti in assets/position_markers/.
 */
data class PositionItem(
    val id: String,
    val title: String,
    val note: String = "",
    val ref: String? = null,
    val raw: JSONObject? = null
)

fun clearPersistentSelections(context: Context) {
    val prefs = context.getSharedPreferences("PositionMarkerPrefs", Context.MODE_PRIVATE)
    prefs.edit().remove("persistent_selections").apply()
}