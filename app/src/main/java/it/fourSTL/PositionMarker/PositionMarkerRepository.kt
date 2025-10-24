package it.fourSTL.PositionMarker

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Legge file JSON dalla cartella assets/position_markers/ e/o dalla memoria interna.
 * e li converte in liste di PositionItem.
 *
 * MAPPATURA CAMPI JSON -> PositionItem:
 * - id: usa "idsp" come identificatore univoco (fallback su "id" numerico)
 * - title: "nome italiano" o "nota" (visualizzato come testo principale)
 * - note: "nome latino" o "nota_b" (visualizzato come sottotitolo)
 * - ref: "ref" o "Ref" (riferimento a sottocategoria)
 */
class PositionMarkerRepository(
    private val context: Context,
    private val basePath: String = "position_markers/"
) {

    init {
        // Crea il file personale.json se non esiste
        val personalJson = File(context.filesDir, "personale.json")
        if (!personalJson.exists()) {
            personalJson.writeText("[]")
        }
    }

    suspend fun loadItems(filename: String): List<PositionItem> = withContext(Dispatchers.IO) {
        try {
            val text = readFileText(filename) ?: return@withContext emptyList()
            val arr = JSONArray(text)
            val result = mutableListOf<PositionItem>()

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)

                // 🔑 ID: priorità a "idsp" (indice univoco), altrimenti "id"
                val id = when {
                    obj.has("idsp") -> obj.getString("idsp")
                    obj.has("id") -> obj.optString("id", obj.optInt("id").toString())
                    else -> "unknown_$i"
                }

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

    /**
     * Estrae il titolo (nome italiano o nota) dal JSON.
     * Cerca prima i campi standard, poi usa ricerca case-insensitive.
     */
    private fun extractTitle(obj: JSONObject): String {
        // 1️⃣ Cerca campi esatti (priorità alta)
        val exactKeys = listOf("nome italiano", "Nome italiano", "Nome Italiano", "NOME ITALIANO", "nota")
        for (k in exactKeys) {
            if (obj.has(k)) return obj.optString(k, "").trim()
        }

        // 2️⃣ Cerca altri campi standard
        val standardKeys = listOf("title", "name", "label", "Main1", "Main2", "Sub1", "Sub2")
        for (k in standardKeys) {
            if (obj.has(k)) return obj.optString(k, "").trim()
        }

        // 3️⃣ Ricerca case-insensitive per "nome italiano"
        val it = obj.keys()
        while (it.hasNext()) {
            val key = it.next()
            if (key.lowercase().contains("nome") && key.lowercase().contains("italiano")) {
                return obj.optString(key, "").trim()
            }
        }

        // 4️⃣ Fallback: primo valore stringa NON tecnico
        val excludeKeys = setOf("id", "idsp", "ref", "nome latino", "nome_latino", "nota_b")
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key.lowercase() !in excludeKeys.map { it.lowercase() }) {
                val v = obj.optString(key, "").trim()
                if (v.isNotEmpty()) return v
            }
        }

        return ""
    }

    /**
     * Estrae la nota (nome latino o nota_b) dal JSON.
     * Cerca prima i campi standard, poi usa ricerca case-insensitive.
     */
    private fun extractNote(obj: JSONObject): String {
        // 1️⃣ Cerca campi esatti (priorità alta)
        val exactKeys = listOf("nome latino", "Nome latino", "Nome Latino", "NOME LATINO", "nota_b")
        for (k in exactKeys) {
            if (obj.has(k)) return obj.optString(k, "").trim()
        }

        // 2️⃣ Cerca altri campi standard
        val standardKeys = listOf("note", "descrizione", "description", "Note", "Descrizione")
        for (k in standardKeys) {
            if (obj.has(k)) return obj.optString(k, "").trim()
        }

        // 3️⃣ Ricerca case-insensitive per "nome latino"
        val it = obj.keys()
        while (it.hasNext()) {
            val key = it.next()
            if (key.lowercase().contains("nome") && key.lowercase().contains("latino")) {
                return obj.optString(key, "").trim()
            }
        }

        return ""
    }

    private fun readFileText(filename: String): String? {
        if (filename == "personale.json") {
            return try {
                val file = File(context.filesDir, "personale.json")
                if (file.exists()) {
                    file.readText()
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else {
            return try {
                context.assets.open(basePath + filename).bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}