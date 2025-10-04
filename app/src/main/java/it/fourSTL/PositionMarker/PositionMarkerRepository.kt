package it.fourSTL.PositionMarker

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Legge file JSON dalla cartella assets/position_markers/
 * e li converte in liste di PositionItem.
 */
class PositionMarkerRepository(
    private val context: Context,
    private val basePath: String = "position_markers/"
) {

    suspend fun loadItems(filename: String): List<PositionItem> = withContext(Dispatchers.IO) {
        try {
            val text = readFileText(filename) ?: return@withContext emptyList()
            val arr = JSONArray(text)
            val result = mutableListOf<PositionItem>()

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id", obj.optInt("id").toString())
                val title = extractTitle(obj)
                val note = extractNote(obj)
                val ref = obj.optString("ref", obj.optString("Ref", null))

                result.add(PositionItem(id = id, title = title, note = note, ref = ref, raw = obj))
            }

            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun extractTitle(obj: JSONObject): String {
        val keys = listOf("title", "name", "label", "Main1", "Main2", "Sub1", "Sub2", "Nome italiano")
        for (k in keys) if (obj.has(k)) return obj.optString(k)
        // fallback: primo valore stringa diverso da id/ref
        val it = obj.keys()
        while (it.hasNext()) {
            val key = it.next()
            val v = obj.optString(key, "")
            if (v.isNotEmpty() && key.lowercase() !in listOf("id", "ref")) return v
        }
        return ""
    }

    private fun extractNote(obj: JSONObject): String {
        val keys = listOf("note", "descrizione", "Nome latino")
        for (k in keys) if (obj.has(k)) return obj.optString(k)
        return ""
    }

    private fun readFileText(filename: String): String? {
        return try {
            context.assets.open(basePath + filename).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
