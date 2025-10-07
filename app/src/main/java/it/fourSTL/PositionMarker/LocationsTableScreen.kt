/*package it.fourSTL.PositionMarker

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview


@Composable
fun LocationsTableScreen(
    context: Context,
    onBack: () -> Unit
) {
    val locations = remember { readLocationsFromJson(context) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Punti salvati", style = MaterialTheme.typography.titleLarge)

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(locations) { loc ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text("${loc.id}", modifier = Modifier.weight(1f))
                    Text("${loc.latitude}", modifier = Modifier.weight(1f))
                    Text("${loc.longitude}", modifier = Modifier.weight(1f))
                    Text("${loc.altitude}", modifier = Modifier.weight(1f))
                    Text(loc.date, modifier = Modifier.weight(1f))
                    Text(loc.hour, modifier = Modifier.weight(1f))
                }
            }
        }

        Button(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Chiudi")
        }
    }
}*/

package it.fourSTL.PositionMarker

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import java.io.File

// Data class per rappresentare una posizione salvata
data class LocationDatas(
    val id: Int,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val date: String,
    val hour: String,
    val idsp: String? = null,              // ✅ Campo idsp
    val nomeItaliano: String? = null,      // ✅ Nome italiano
    val nomeLatino: String? = null         // ✅ Nome latino
)

/**
 * Legge le posizioni dal file JSON salvato
 */
fun readLocationsFromJsons(context: Context): List<LocationDatas> {
    val file = File(context.filesDir, "locations.json")

    if (!file.exists() || file.length() == 0L) {
        return emptyList()
    }

    return try {
        val jsonArray = JSONArray(file.readText())
        val locations = mutableListOf<LocationDatas>()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)

            locations.add(
                LocationDatas(
                    id = obj.optInt("id", 0),
                    latitude = obj.getDouble("latitude"),
                    longitude = obj.getDouble("longitude"),
                    altitude = obj.optDouble("altitude", 0.0),
                    date = obj.optString("date", "N/A"),
                    hour = obj.optString("hour", "N/A"),
                    // ✅ Leggi i metadati se presenti
                    idsp = obj.optString("idsp").takeIf { it.isNotEmpty() },
                    nomeItaliano = obj.optString("nome italiano").takeIf { it.isNotEmpty() },
                    nomeLatino = obj.optString("nome latino").takeIf { it.isNotEmpty() }
                )
            )
        }

        // Ordina per ID decrescente (più recenti prima)
        locations.sortedByDescending { it.id }

    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

/**
 * Elimina una posizione dal file JSON
 */
fun deleteLocationFromJson(context: Context, locationId: Int): Boolean {
    val file = File(context.filesDir, "locations.json")

    return try {
        val jsonArray = JSONArray(file.readText())
        val newArray = JSONArray()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            if (obj.optInt("id") != locationId) {
                newArray.put(obj)
            }
        }

        file.writeText(newArray.toString(2))
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsTableScreen(
    context: Context,
    onBack: () -> Unit,
    onPointClick: ((Double, Double) -> Unit)? = null  // Callback per centrare mappa
) {
    // Stato per ricaricare la lista dopo eliminazione
    var locations by remember { mutableStateOf(readLocationsFromJsons(context)) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var locationToDelete by remember { mutableStateOf<LocationDatas?>(null) }

    // Funzione per ricaricare i dati
    fun reloadLocations() {
        locations = readLocationsFromJsons(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Punti salvati",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${locations.size} punt${if (locations.size == 1) "o" else "i"} totali",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Lista dei punti
        if (locations.isEmpty()) {
            // Messaggio quando non ci sono punti
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "📍 Nessun punto salvato",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Gray
                    )
                    Text(
                        "Salva un punto GPS per visualizzarlo qui",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(locations) { location ->
                    LocationCard(
                        location = location,
                        onClick = {
                            onPointClick?.invoke(location.latitude, location.longitude)
                        },
                        onDelete = {
                            locationToDelete = location
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Pulsante chiudi
        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Chiudi", style = MaterialTheme.typography.titleMedium)
        }
    }

    // Dialog di conferma eliminazione
    if (showDeleteDialog && locationToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Elimina punto") },
            text = {
                Text(
                    "Vuoi eliminare il punto #${locationToDelete?.id}?\n" +
                            "Questa azione non può essere annullata."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        locationToDelete?.let {
                            if (deleteLocationFromJson(context, it.id)) {
                                reloadLocations()
                            }
                        }
                        showDeleteDialog = false
                        locationToDelete = null
                    }
                ) {
                    Text("Elimina", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annulla")
                }
            }
        )
    }
}

@Composable
fun LocationCard(
    location: LocationDatas,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (location.idsp != null)
                Color(0xFFFFF9C4)  // 🟡 Giallo chiaro se ha metadati
            else
                MaterialTheme.colorScheme.surface  // ⚪ Bianco se solo GPS
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header della card: ID e Data/Ora
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Punto #${location.id}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        location.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Text(
                        location.hour.take(8),  // Mostra solo HH:mm:ss
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Coordinate GPS
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(
                        "📍 Latitudine:  ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        String.format("%.6f", location.latitude),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row {
                    Text(
                        "📍 Longitudine: ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        String.format("%.6f", location.longitude),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row {
                    Text(
                        "⛰️ Altitudine:  ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        String.format("%.1f m", location.altitude),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // ✅ METADATI (se presenti)
            if (location.idsp != null) {
                Spacer(Modifier.height(12.dp))
                Divider(color = Color.Gray.copy(alpha = 0.3f))
                Spacer(Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Codice IDSP
                    Row {
                        Text(
                            "🔖 Codice: ",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            location.idsp,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Nome italiano
                    location.nomeItaliano?.let { nome ->
                        Row {
                            Text(
                                "🇮🇹 ",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                nome,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Nome latino
                    location.nomeLatino?.let { nome ->
                        Row {
                            Text(
                                "🌿 ",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                nome,
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Pulsanti azione
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pulsante vai al punto
                TextButton(
                    onClick = onClick,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Vai al punto")
                }

                Spacer(Modifier.width(8.dp))

                // Pulsante elimina
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Elimina")
                }
            }
        }
    }
}