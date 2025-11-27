package it.fourSTL.PositionMarker

import android.content.Context
import org.json.JSONObject
import kotlin.collections.remove

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