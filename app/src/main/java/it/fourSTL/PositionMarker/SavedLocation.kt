package it.fourSTL.PositionMarker

import android.content.Context
import org.json.JSONArray
import java.io.File

data class SavedLocation(
    val id: Int,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val date: String,
    val hour: String,
)

fun readLocationsFromJson(context: Context): List<SavedLocation> {
    val file = File(context.filesDir, "locations.json")
    if (!file.exists()) return emptyList()

    val content = file.readText()
    if (content.isBlank()) return emptyList()

    val jsonArray = JSONArray(content)
    val locations = mutableListOf<SavedLocation>()

    for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        val id = if (obj.has("id")) obj.getInt("id") else i // fallback
        val latitude = obj.optDouble("latitude", 0.0)
        val longitude = obj.optDouble("longitude", 0.0)
        val altitude = obj.optDouble("altitude", 0.0)
        val date = obj.optString("date", "")
        val hour = obj.optString("hour", "")
        locations.add(SavedLocation(id, latitude, longitude, altitude, date, hour))
    }

    return locations
}

