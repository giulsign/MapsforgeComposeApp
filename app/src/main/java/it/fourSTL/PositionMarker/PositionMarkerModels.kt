package it.fourSTL.PositionMarker

import org.json.JSONObject

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