package it.fourSTL.PositionMarker

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import java.io.File
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.asPaddingValues

// Data class to represent a location
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


fun searchImageOnWeb(context: Context, query: String) {

    val encodedQuery = Uri.encode(query)

    val searchUrl = "https://www.google.com/search?tbm=isch&q=$encodedQuery"

    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse(searchUrl)
    }

    context.startActivity(intent)
}


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
    onPointClick: (List<LocationDatas>) -> Unit
) {
    var locations by remember { mutableStateOf(readLocationsFromJsons(context)) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var locationToDelete by remember { mutableStateOf<LocationDatas?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLocationId by remember { mutableStateOf<Int?>(null) }


    fun reloadLocations() {
        locations = readLocationsFromJsons(context)
    }

    val filteredLocations = if (searchQuery.isBlank()) {
        locations
    } else {
        locations.filter {
            it.nomeItaliano?.contains(searchQuery, ignoreCase = true) == true ||
                    it.nomeLatino?.contains(searchQuery, ignoreCase = true) == true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.systemBars.asPaddingValues())
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
                    "Saved points",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Total ${locations.size} point${if (locations.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search by italian or latin name") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") }
        )

        Spacer(Modifier.height(16.dp))


        // Points list
        if (locations.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📍 No point saved", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                    Text("Save a point before to show here", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredLocations) { location ->
                    LocationCard(
                        location = location,
                        isSelected = location.id == selectedLocationId,
                        onClick = {
                            selectedLocationId = location.id
                            if (searchQuery.isBlank()) {
                                onPointClick(listOf(location))
                            } else {
                                onPointClick(filteredLocations)
                            }
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

        // Button close
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Close", style = MaterialTheme.typography.titleMedium)
        }
    }

    // Dialog confirm
    if (showDeleteDialog && locationToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete point") },
            text = { Text("Do you want to delete this point? #${locationToDelete?.id}?\nThis action cannot be undone.") },
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
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Undo") }
            }
        )
    }
}

@Composable
fun LocationCard(
    location: LocationDatas,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val backgroundColor = if (isSelected) Color(0xFF81D4FA) else if (location.idsp != null) Color(0xFFFFF9C4) else MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onClick() }
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top //
                ) {
                    Text(
                        text = "Punto #${location.id}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(location.date, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text(location.hour.take(8), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }

                Spacer(Modifier.height(12.dp))


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


            Spacer(Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                IconButton(onClick = onClick) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Show Point Location",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }


                location.nomeLatino?.let { query ->
                    if (query.isNotBlank()) {
                        IconButton(onClick = { searchImageOnWeb(context, query) }) {
                            Icon(
                                Icons.Default.ImageSearch,
                                contentDescription = "Search Image on Web",
                                tint = Color(blue = 0x80, green = 0x80, red = 0x80)
                            )
                        }
                    }
                }


                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
