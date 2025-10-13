package it.fourSTL.PositionMarker

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ImageSearch // <-- Icona per la ricerca immagini
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext // <-- Necessario per usare il context nel Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import java.io.File

// Data class per rappresentare una posizione salvata (invariata)
data class LocationDatas(
    val id: Int,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val date: String,
    val hour: String,
    val idsp: String? = null,
    val nomeItaliano: String? = null,
    val nomeLatino: String? = null
)

/**
 * NUOVA FUNZIONE: Avvia la ricerca web del metadato salvato
 */
fun searchImageOnWeb(context: Context, query: String) {
    // Codifica la query per assicurarsi che sia un URL valido
    val encodedQuery = Uri.encode(query)
    // Costruisce l'URL per la ricerca di immagini su Google
    val searchUrl = "https://www.google.com/search?tbm=isch&q=$encodedQuery"

    // Crea un Intent per aprire il browser web
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse(searchUrl)
    }

    // Avvia l'attività del browser
    context.startActivity(intent)
}

/**
 * Legge le posizioni dal file JSON salvato (invariata)
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
                    idsp = obj.optString("idsp").takeIf { it.isNotEmpty() },
                    nomeItaliano = obj.optString("nome italiano").takeIf { it.isNotEmpty() },
                    nomeLatino = obj.optString("nome latino").takeIf { it.isNotEmpty() }
                )
            )
        }

        locations.sortedByDescending { it.id }

    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

/**
 * Elimina una posizione dal file JSON (invariata)
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
    onPointClick: ((Double, Double) -> Unit)? = null
) {
    var locations by remember { mutableStateOf(readLocationsFromJsons(context)) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var locationToDelete by remember { mutableStateOf<LocationDatas?>(null) }

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
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📍 Nessun punto salvato", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                    Text("Salva un punto GPS per visualizzarlo qui", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
                        onClick = { onPointClick?.invoke(location.latitude, location.longitude) },
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
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Chiudi", style = MaterialTheme.typography.titleMedium)
        }
    }

    // Dialog di conferma eliminazione
    if (showDeleteDialog && locationToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Elimina punto") },
            text = { Text("Vuoi eliminare il punto #${locationToDelete?.id}?\nQuesta azione non può essere annullata.") },
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
                TextButton(onClick = { showDeleteDialog = false }) { Text("Annulla") }
            }
        )
    }
}

/*@Composable
fun LocationCard(
    location: LocationDatas,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    // MODIFICA: Ottieni il context per usarlo nella funzione di ricerca
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (location.idsp != null) Color(0xFFFFF9C4) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            // Header della card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Punto #${location.id}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Column(horizontalAlignment = Alignment.End) {
                    Text(location.date, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text(location.hour.take(8), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Coordinate GPS
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text("📍 Latitudine:  ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(String.format("%.6f", location.latitude), style = MaterialTheme.typography.bodyMedium)
                }
                Row {
                    Text("📍 Longitudine: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(String.format("%.6f", location.longitude), style = MaterialTheme.typography.bodyMedium)
                }
                Row {
                    Text("⛰️ Altitudine:  ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(String.format("%.1f m", location.altitude), style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Metadati (se presenti)
            if (location.idsp != null) {
                Spacer(Modifier.height(12.dp))
                Divider(color = Color.Gray.copy(alpha = 0.3f))
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Text("🔖 Codice: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(location.idsp, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    location.nomeItaliano?.let { nome ->
                        Row {
                            Text("🇮🇹 ", style = MaterialTheme.typography.bodyMedium)
                            Text(nome, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                    location.nomeLatino?.let { nome ->
                        Row {
                            Text("🌿 ", style = MaterialTheme.typography.bodySmall)
                            Text(nome, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = Color.Gray)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // MODIFICA: Sezione Pulsanti Azione
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // MODIFICA: Il pulsante "Cerca Web" ora è il primo
                // e viene mostrato solo se c'è un metadato da cercare
                location.nomeLatino?.let { query ->
                    if (query.isNotBlank()) {
                        TextButton(
                            onClick = { searchImageOnWeb(context, query) },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color(0xFF0288D1) // Un colore blu per distinguerlo
                            )
                        ) {
                            Icon(Icons.Default.ImageSearch, contentDescription = "Cerca Immagine", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cerca Web")
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }

                // Pulsante vai al punto
                TextButton(
                    onClick = onClick,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Vai al punto", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Vai al punto")
                }

                Spacer(Modifier.width(8.dp))

                // Pulsante elimina
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Elimina", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Elimina")
                }
            }
        }
    }
}*/

@Composable
fun LocationCard(
    location: LocationDatas,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (location.idsp != null) Color(0xFFFFF9C4) else MaterialTheme.colorScheme.surface
        )
    ) {
        // Row principale che separa le info a sinistra dai pulsanti a destra
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 16.dp), // Padding applicato qui
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- SEZIONE SINISTRA: INFORMAZIONI ( occupa tutto lo spazio disponibile) ---
            Column(
                modifier = Modifier
                    .weight(1f) // Occupa tutto lo spazio rimanente
                    .clickable { onClick() } // Cliccare qui porta alla mappa
            ) {
                // Header della card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top // Allinea in alto per date e ID
                ) {
                    Text(
                        text = "Punto #${location.id}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f, fill = false) // Evita che il testo vada a capo inutilmente
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(location.date, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text(location.hour.take(8), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Coordinate GPS
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row {
                        Text("📍 Lat: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(String.format("%.6f", location.latitude), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row {
                        Text("📍 Lon: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(String.format("%.6f", location.longitude), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Metadati (se presenti)
                location.nomeItaliano?.let { nome ->
                    Spacer(Modifier.height(12.dp))
                    Divider(color = Color.Gray.copy(alpha = 0.3f))
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🇮🇹 ", style = MaterialTheme.typography.bodyMedium)
                            Text(nome, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        location.nomeLatino?.let { nomeLat ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🌿 ", style = MaterialTheme.typography.bodySmall)
                                Text(nomeLat, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            // --- SEZIONE DESTRA: PULSANTI IN COLONNA ---
            // Spacer per separare visivamente le info dai pulsanti
            Spacer(Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Pulsante "Vai al punto" (ora solo icona per risparmiare spazio)
                IconButton(onClick = onClick) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Vai al punto",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Pulsante "Cerca Web" (solo icona)
                // Appare solo se il metadato esiste
                location.nomeLatino?.let { query ->
                    if (query.isNotBlank()) {
                        IconButton(onClick = { searchImageOnWeb(context, query) }) {
                            Icon(
                                Icons.Default.ImageSearch,
                                contentDescription = "Cerca Immagine",
                                tint = Color(0xFF0288D1)
                            )
                        }
                    }
                }

                // Pulsante "Elimina" (solo icona)
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Elimina",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

