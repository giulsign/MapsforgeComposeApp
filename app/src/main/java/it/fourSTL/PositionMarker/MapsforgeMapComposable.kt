package it.fourSTL.PositionMarker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.layer.overlay.Marker
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import androidx.core.content.edit
import androidx.compose.ui.zIndex
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import android.app.Activity
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import java.io.FileInputStream
import java.io.FileOutputStream
import androidx.core.content.res.ResourcesCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.LocalContext

// 🔹 UTILITY per memorizzare metadati persistenti
fun savePersistentMetadata(context: Context, metadata: Map<String, String>) {
    val prefs = context.getSharedPreferences("metadata_prefs", Context.MODE_PRIVATE)
    val json = JSONObject(metadata).toString()
    prefs.edit().putString("persistent_metadata", json).apply()
}

fun loadPersistentMetadata(context: Context): Map<String, String> {
    val prefs = context.getSharedPreferences("metadata_prefs", Context.MODE_PRIVATE)
    val json = prefs.getString("persistent_metadata", "{}") ?: "{}"
    val obj = JSONObject(json)
    return obj.keys().asSequence().associateWith { obj.getString(it) }
}

fun clearPersistentMetadata(context: Context) {
    val prefs = context.getSharedPreferences("metadata_prefs", Context.MODE_PRIVATE)
    prefs.edit().remove("persistent_metadata").apply()
}

// 🔹 Funzioni per il nuovo sistema di selezioni persistenti (importate da SelectionScreen.kt)


// Copia italy.map nella cartella di destinazione
private fun copyMapFileIfNeeded(context: Context, mapFileName: String): File {
    val destFile = File(context.getExternalFilesDir("maps"), mapFileName)

    if (!destFile.exists()) {
        destFile.parentFile?.mkdirs()
        context.assets.open("maps/$mapFileName").use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
    return destFile
}

// Mostra la posizione di partenza sulla mappa
fun showSavedLocationOnMapForge(context: Context, map: org.mapsforge.map.android.view.MapView) {
    val file = File(context.filesDir, "auto_locations.json")

    if (!file.exists() || file.length() == 0L) {
        Toast.makeText(context, "⚠️ Nessun punto salvato trovato.", Toast.LENGTH_LONG).show()
        return
    }

    try {
        val jsonArray = JSONArray(file.readText())
        if (jsonArray.length() == 0) {
            Toast.makeText(context, "⚠️ Nessun punto valido nel file JSON.", Toast.LENGTH_LONG).show()
            return
        }

        val locationJson = jsonArray.getJSONObject(0)
        val latitude = locationJson.getDouble("latitude")
        val longitude = locationJson.getDouble("longitude")

        val geoPoint = LatLong(latitude, longitude)
        val drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_marker, null)
        val markerBitmap = AndroidGraphicFactory.convertToBitmap(drawable)
        val marker = Marker(geoPoint, markerBitmap, 0, -markerBitmap.height / 2)

        map.layerManager.layers.add(marker)
        map.model.mapViewPosition.setCenter(geoPoint)

        Toast.makeText(context, "📍 Punto di partenza visualizzato sulla mappa", Toast.LENGTH_LONG).show()

    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "❌ Errore nella lettura del punto salvato.", Toast.LENGTH_LONG).show()
    }
}

// ID sequenziale
fun getNextId(context: Context): Int {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val nextId = prefs.getInt("last_id", 0) + 1
    prefs.edit { putInt("last_id", nextId) }
    return nextId
}

// Cancella dati tabella JSON
fun clearSavedLocations(context: Context) {
    val file = File(context.filesDir, "locations.json")
    if (file.exists()) {
        file.writeText("[]")
    }
    Toast.makeText(context, "Dati tabella JSON cancellati", Toast.LENGTH_LONG).show()
}

// Cancella posizione auto
fun clearSavedLocationsAuto(context: Context) {
    val file = File(context.filesDir, "auto_locations.json")
    if (file.exists()) {
        file.writeText("[]")
    }
    Toast.makeText(context, "Dati posizione di partenza cancellati", Toast.LENGTH_LONG).show()
}

// Salva posizione GPS su file JSON
fun saveLocationToJson(context: Context, location: Location, metadataMap: Map<String, String> = emptyMap()) {
    val pointId = getNextId(context)
    val file = File(context.filesDir, "locations.json")

    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val hour = SimpleDateFormat("HH:mm:ss.S", Locale.getDefault()).format(Date())

    val locationJson = JSONObject().apply {
        put("id", pointId)
        put("latitude", location.latitude)
        put("longitude", location.longitude)
        put("altitude", location.altitude)
        put("date", date)
        put("hour", hour)
        metadataMap.forEach { (key, value) -> put(key, value) }
    }

    val jsonArray: JSONArray = if (file.exists() && file.length() > 0) {
        JSONArray(file.readText())
    } else {
        JSONArray()
    }

    jsonArray.put(locationJson)
    file.writeText(jsonArray.toString(2))

    Toast.makeText(context, "Nuovo punto aggiunto a tabella JSON", Toast.LENGTH_LONG).show()
}

// Salva posizione di partenza (una sola volta)
fun saveLocationToJsonAuto(context: Context, location: Location) {
    val file = File(context.filesDir, "auto_locations.json")

    if (file.exists() && file.length() > 0) {
        try {
            val jsonArray = JSONArray(file.readText())
            if (jsonArray.length() > 0) {
                Toast.makeText(
                    context,
                    "⚠️ Esiste già un punto salvato. Eliminare prima di aggiungerne un altro.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        } catch (e: Exception) {
            file.delete()
        }
    }

    val pointId = getNextId(context)
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val hour = SimpleDateFormat("HH:mm:ss.S", Locale.getDefault()).format(Date())

    val locationJson = JSONObject().apply {
        put("id", pointId)
        put("latitude", location.latitude)
        put("longitude", location.longitude)
        put("altitude", location.altitude)
        put("date", date)
        put("hour", hour)
    }

    val jsonArray = JSONArray().apply { put(locationJson) }
    file.writeText(jsonArray.toString(2))

    Toast.makeText(context, "✅ Nuovo punto di partenza salvato.", Toast.LENGTH_LONG).show()
}

// Esporta JSON in Download
fun exportJsonToDownload(context: Context) {
    try {
        val sourceFile = File(context.filesDir, "locations.json")

        if (!sourceFile.exists()) {
            Toast.makeText(context, "Nessun file JSON trovato", Toast.LENGTH_SHORT).show()
            return
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val fileName = "fourSTLPositionMarker_locations_$timestamp.json"

        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadDir.exists()) downloadDir.mkdirs()

        val destFile = File(downloadDir, fileName)

        FileInputStream(sourceFile).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }

        Toast.makeText(context, "File copiato in Download come $fileName", Toast.LENGTH_LONG).show()

    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Errore durante la copia: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

// Main composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun fourSTLPositionMarkerComposable(
    context: Context,
    modifier: Modifier = Modifier,
    mapFileName: String = "italy.map"
) {
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var showTable by remember { mutableStateOf(false) }
    var showSelectionScreen by remember { mutableStateOf(false) }
    var showTableAuto by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showConfirmDialogAuto by remember { mutableStateOf(false) }
    var showConfirmMessage by remember { mutableStateOf(false) }
    var showConfirmMessageAuto by remember { mutableStateOf(false) }
    var showGpsDialog by remember { mutableStateOf(false) }
    var carMarker: Marker? by remember { mutableStateOf(null) }
    var showCarMarker by remember { mutableStateOf(false) }
    var selectedMetadata by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showLicenses by remember { mutableStateOf(false) }

    // 🔹 Carica le selezioni persistenti all'avvio
    val persistentSelectionsState = remember {
        mutableStateOf(loadPersistentSelectionsSet(context))
    }
    var persistentMetadata by remember { mutableStateOf(loadPersistentMetadata(context)) }
    var filteredMarkers by remember { mutableStateOf<List<Marker>>(emptyList()) }
    var buttonsVisible by remember { mutableStateOf(false) }

    var savedLocations by remember { mutableStateOf(listOf<String>()) }

    val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // Launcher per chiedere permessi
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        if (granted) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    userLocation = location
                }
            }
        }
    }

    // Verifica file mappa
    val mapFile = copyMapFileIfNeeded(context, mapFileName)
    if (!mapFile.exists()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.LightGray)
                .border(25.dp, Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("File mappa non trovato: ${mapFile.absolutePath}")
        }
        return
    }

    // Marker rosso per la posizione utente
    var userMarker: Marker? by remember { mutableStateOf(null) }

    fun createRedMarker(context: Context, latLong: LatLong): Marker {
        val drawable = ContextCompat.getDrawable(context, R.drawable.presence_online)
        val bitmap = AndroidGraphicFactory.convertToBitmap(drawable)
        return Marker(latLong, bitmap, 0, -bitmap.height / 2)
    }

    // Gestione permessi e aggiornamento continuo posizione
    LaunchedEffect(Unit) {
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                hasLocationPermission = true

                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        userLocation = location
                    }
                }

                // Aggiornamento continuo in tempo reale
                val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                    com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                    2000
                ).build()

                val locationCallback = object : com.google.android.gms.location.LocationCallback() {
                    override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                        result.lastLocation?.let { loc ->
                            userLocation = loc
                        }
                    }
                }

                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    android.os.Looper.getMainLooper()
                )

                // Timeout GPS: 20 secondi
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (userLocation == null) {
                        showGpsDialog = true
                    }
                }, 20000)
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    // Crea e mostra MapView
    Box(
        modifier = modifier
            .fillMaxSize()
            .border(6.dp, Color.Black)
    ) {
        AndroidView(
            factory = {
                AndroidGraphicFactory.createInstance(context.applicationContext)
                val mapView = MapView(context).apply {
                    mapScaleBar.isVisible = false
                    setBuiltInZoomControls(false)
                    setZoomLevelMin(2.toByte())
                    setZoomLevelMax(20.toByte())
                }

                val mapFileReader = MapFile(mapFile)
                val tileCache: TileCache = AndroidUtil.createTileCache(
                    context,
                    "mapcache",
                    mapView.model.displayModel.tileSize,
                    1f,
                    mapView.model.frameBufferModel.overdrawFactor
                )

                val renderer = TileRendererLayer(
                    tileCache,
                    mapFileReader,
                    mapView.model.mapViewPosition,
                    AndroidGraphicFactory.INSTANCE
                ).apply {
                    setXmlRenderTheme(AssetsRenderTheme(context))
                }
                mapView.layerManager.layers.add(renderer)

                // Posizione iniziale: Milano
                val startLatLong = LatLong(45.4642, 9.19)
                mapView.model.mapViewPosition.setCenter(startLatLong)
                mapView.model.mapViewPosition.setZoomLevel(15.toByte())

                userMarker = createRedMarker(context, startLatLong)
                mapView.layerManager.layers.add(userMarker)

                mapViewRef = mapView
                mapView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Pulsante MyLocation
        //if (buttonsVisible) {
        FloatingActionButton(
            onClick = {
                userLocation?.let { loc ->
                    val latLong = LatLong(loc.latitude, loc.longitude)
                    mapViewRef?.model?.mapViewPosition?.apply {
                        setCenter(latLong)
                        setZoomLevel(15.toByte())
                    }
                }
                filteredMarkers.forEach { mapViewRef?.layerManager?.layers?.remove(it) }
                filteredMarkers = emptyList()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
                .zIndex(2f),
            shape = CircleShape,
            containerColor = Color.White
        ) {
            Icon(
                painter = painterResource(id = R.drawable.my_location),
                contentDescription = "Vai alla mia posizione",
                tint = Color.Unspecified
            )
            //}
        }

        // 🔹 Pulsante salva posizione
        //if (buttonsVisible) {
        FloatingActionButton(
            onClick = {
                userLocation?.let { loc ->
                    // ✅ RICARICA i metadati persistenti PRIMA di salvare
                    persistentMetadata = loadPersistentMetadata(context)

                    val finalMetadata = persistentMetadata + selectedMetadata
                    saveLocationToJson(context, loc, finalMetadata)

                    // Reset SOLO dei temporanei
                    selectedMetadata = emptyMap()
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 30.dp, vertical = 175.dp)
                .zIndex(2f),
            containerColor = Color.White,
            shape = CircleShape
        ) {
            Icon(
                painter = painterResource(id = R.drawable.save),
                contentDescription = "Salva",
                tint = Color.Unspecified
            )
            //}
        }

        // Pulsante salva posizione auto
        //if (buttonsVisible) {
        FloatingActionButton(
            onClick = {
                userLocation?.let { loc ->
                    saveLocationToJsonAuto(context, loc)
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = 30.dp, vertical = 175.dp)
                .zIndex(2f),
            containerColor = Color.White,
            shape = CircleShape
        ) {
            Icon(
                painter = painterResource(id = R.drawable.save_car),
                contentDescription = "Salva posizione partenza",
                tint = Color.Unspecified
            )
            //}
        }

        // Pulsante cancella tabella punti
        if (buttonsVisible) {
            FloatingActionButton(
                onClick = { showConfirmDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 30.dp, vertical = 200.dp)
                    .zIndex(2f),
                containerColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.delete),
                    contentDescription = "Cancella punti salvati",
                    tint = Color.Unspecified)
            }
        }

        // Dialogo conferma cancellazione punti
        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            clearSavedLocations(context)
                            showConfirmDialog = false
                            showConfirmMessage = true
                        }
                    ) {
                        Text("Conferma")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("Annulla")
                    }
                },
                title = { Text("Conferma eliminazione dei punti salvati") },
                text = {
                    Text("Vuoi davvero cancellare la tabella dei punti salvati? Questa azione non può essere annullata.")
                }
            )
        }

        // Messaggio finale dopo cancellazione
        if (showConfirmMessage) {
            AlertDialog(
                onDismissRequest = { showConfirmMessage = false },
                title = { Text("Cancellazione completata") },
                text = { Text("La tabella dei punti salvati è stata cancellata.") },
                confirmButton = {
                    TextButton(onClick = { showConfirmMessage = false }) {
                        Text("OK")
                    }
                }
            )
        }

        // Pulsante esportazione JSON
        if (buttonsVisible) {
            FloatingActionButton(
                onClick = { exportJsonToDownload(context) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 30.dp, vertical = 275.dp)
                    .zIndex(2f),
                containerColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.file_export),
                    contentDescription = "Esporta punti salvati in json",
                    tint = Color.Unspecified)
            }
        }

        // Pulsante cancella posizione auto
        if (buttonsVisible) {
            FloatingActionButton(
                onClick = { showConfirmDialogAuto = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = 30.dp, vertical = 325.dp)
                    .zIndex(2f),
                containerColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.delete),
                    contentDescription = "Cancella punti auto",
                    tint = Color.Unspecified
                )
            }
        }

        // Dialogo conferma cancellazione auto
        if (showConfirmDialogAuto) {
            AlertDialog(
                onDismissRequest = { showConfirmDialogAuto = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            clearSavedLocationsAuto(context)
                            showConfirmDialogAuto = false
                            showConfirmMessageAuto = true
                        }
                    ) {
                        Text("Conferma")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialogAuto = false }) {
                        Text("Annulla")
                    }
                },
                title = { Text("Conferma eliminazione posizione auto") },
                text = {
                    Text("Vuoi davvero cancellare la posizione salvata? Questa azione non può essere annullata.")
                }
            )
        }

        // Messaggio finale dopo cancellazione auto
        if (showConfirmMessageAuto) {
            AlertDialog(
                onDismissRequest = { showConfirmMessageAuto = false },
                title = { Text("Cancellazione completata") },
                text = { Text("La posizione di partenza è stata cancellata.") },
                confirmButton = {
                    TextButton(onClick = { showConfirmMessageAuto = false }) {
                        Text("OK")
                    }
                }
            )
        }

        // Pulsanti Zoom
        //if (buttonsVisible) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 30.dp)
                    .background(Color.Transparent),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FloatingActionButton(
                    onClick = { mapViewRef?.model?.mapViewPosition?.zoomOut() },
                    containerColor = Color.White, // 🔹 Colore del pulsante
                    shape = CircleShape // 🔹 Forma del pulsante
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.zoom_out),
                        contentDescription = "Zoom out",
                        tint = Color.Unspecified
                    )
                }
                FloatingActionButton(
                    onClick = { mapViewRef?.model?.mapViewPosition?.zoomIn() },
                    containerColor = Color.White, // 🔹 Colore del pulsante
                    shape = CircleShape // 🔹 Forma del pulsante
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.zoom_in),
                        contentDescription = "Zoom in",
                        tint = Color.Unspecified
                    )
                }
            //}
        }

        // Marker GPS aggiornato
        LaunchedEffect(userLocation) {
            userLocation?.let { loc ->
                val latLong = LatLong(loc.latitude, loc.longitude)
                if (userMarker == null && mapViewRef != null) {
                    userMarker = createRedMarker(context, latLong)
                    mapViewRef?.layerManager?.layers?.add(userMarker)
                } else {
                    userMarker?.latLong = latLong
                }
            }
        }

        // Visualizzazione tabella punti salvati
        if (buttonsVisible) {
            FloatingActionButton(
                onClick = { showTable = true },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 30.dp, vertical = 325.dp)
                    .zIndex(2f),
                containerColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.archive_light),
                    contentDescription = "Visualizza tabella",
                    tint = Color.Unspecified
                )
            }
        }

        if (showTable) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .zIndex(998f)
                    .clickable { showTable = false }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.85f)
                        .background(Color.White, shape = MaterialTheme.shapes.large)
                        .zIndex(999f)
                        .clickable(enabled = false) { }
                ) {
                    LocationsTableScreen(
                        context = context,
                        onBack = { showTable = false },
                        onPointClick = { locations ->
                            filteredMarkers.forEach { mapViewRef?.layerManager?.layers?.remove(it) }

                            val markers = locations.map { loc ->
                                val latLong = LatLong(loc.latitude, loc.longitude)
                                val drawable = ResourcesCompat.getDrawable(
                                    context.resources,
                                    R.drawable.ic_marker_blue,
                                    null
                                )
                                val markerBitmap = AndroidGraphicFactory.convertToBitmap(drawable)
                                Marker(latLong, markerBitmap, 0, -markerBitmap.height / 2)
                            }

                            markers.forEach { mapViewRef?.layerManager?.layers?.add(it) }
                            filteredMarkers = markers

                            if (locations.isNotEmpty()) {
                                val firstLocation = locations.first()
                                val latLong = LatLong(firstLocation.latitude, firstLocation.longitude)
                                mapViewRef?.model?.mapViewPosition?.apply {
                                    setCenter(latLong)
                                    setZoomLevel(17.toByte())
                                }
                            }

                            showTable = false
                        }
                    )
                }
            }
        }

        // Pulsante mostra/nascondi punto auto
        //if (buttonsVisible) {
            FloatingActionButton(
                onClick = {
                    mapViewRef?.let { map ->
                        if (!showCarMarker) {
                            val file = File(context.filesDir, "auto_locations.json")
                            if (file.exists() && file.length() > 0) {
                                try {
                                    val jsonArray = JSONArray(file.readText())
                                    if (jsonArray.length() > 0) {
                                        val locationJson = jsonArray.getJSONObject(0)
                                        val latitude = locationJson.getDouble("latitude")
                                        val longitude = locationJson.getDouble("longitude")

                                        val geoPoint = LatLong(latitude, longitude)

                                        val drawable = ResourcesCompat.getDrawable(
                                            context.resources,
                                            R.drawable.ic_marker,
                                            null
                                        )
                                        val markerBitmap = AndroidGraphicFactory.convertToBitmap(drawable)

                                        carMarker = Marker(geoPoint, markerBitmap, 0, -markerBitmap.height / 2)
                                        map.layerManager.layers.add(carMarker)

                                        map.model.mapViewPosition.setCenter(geoPoint)

                                        showCarMarker = true
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "❌ Errore lettura punto auto", Toast.LENGTH_LONG)
                                        .show()
                                }
                            } else {
                                Toast.makeText(context, "⚠️ Nessun punto auto salvato.", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            carMarker?.let { marker ->
                                map.layerManager.layers.remove(marker)
                            }
                            carMarker = null
                            showCarMarker = false
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = 30.dp, vertical = 250.dp)
                    .zIndex(2f),
                containerColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.show_start),
                    contentDescription = "Mostra punto partenza",
                    tint = Color.Unspecified
                )
            //}
        }

        // 🔹 Pulsante apertura SelectionScreen
        //if (buttonsVisible) {
            FloatingActionButton(
                onClick = { showSelectionScreen = true },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 30.dp, vertical = 250.dp)
                    .zIndex(2f),
                containerColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.list),
                    contentDescription = "Apri tabella selezione",
                    tint = Color.Unspecified
                )
            //}
        }

        // 🔹 Overlay SelectionScreen
        if (showSelectionScreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .zIndex(1000f)
            ) {
                SelectionScreen(
                    onDismiss = { showSelectionScreen = false },
                    onSave = { metadataMap: Map<String, String>, _ ->
                        showSelectionScreen = false

                        // Metadata temporanei (gialli)
                        selectedMetadata = metadataMap

                        // ✅ RICARICA i metadati e le selezioni persistenti
                        persistentMetadata = loadPersistentMetadata(context)
                        persistentSelectionsState.value = loadPersistentSelectionsSet(context)

                        Toast.makeText(
                            context,
                            "✅ Temporanei: ${metadataMap.size} | Persistenti: ${persistentMetadata.size}",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    persistentSelections = persistentSelectionsState.value
                )
            }
        }

        // 🔹 Pulsante reset selezioni persistenti
        if (buttonsVisible) {
            FloatingActionButton(
                onClick = {
                    // Usa la funzione completa da SelectionScreen.kt
                    clearAllPersistentData(context)

                    persistentSelectionsState.value = mutableSetOf()
                    persistentMetadata = emptyMap()

                    Toast.makeText(context, "Selezioni persistenti resettate", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 30.dp, vertical = 350.dp)
                    .zIndex(2f),
                containerColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.reset_selection),
                    contentDescription = "Reset selezioni persistenti",
                    tint = Color.Unspecified
                )
            }
        }

        // Pulsante toggle visibilità pulsanti
        FloatingActionButton(
            onClick = {
                buttonsVisible = !buttonsVisible
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp)
                .zIndex(2f),
            containerColor = Color.White,
            shape = CircleShape
        ) {
            if (buttonsVisible)
                Icon(
                    painter = painterResource(id = R.drawable.settings_visible),
                    contentDescription = "Toggle pulsanti",
                    tint = Color.Unspecified
                )
            else
                Icon(
                    painter = painterResource(id = R.drawable.settings_invisible),
                    contentDescription = "Toggle pulsanti",
                    tint = Color.Unspecified
                )
        }


        // Pulsante per le licenze
        FloatingActionButton(
            onClick = { showLicenses = true },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 30.dp, vertical = 60.dp)
                .zIndex(2f), // Assicura che stia sopra la mappa
            containerColor = Color.Yellow,
            shape = CircleShape
        ) {
            Icon(
                painter = painterResource(id = R.drawable.license),
                contentDescription = "Chiudi app",
                tint = Color.Unspecified
            )
        }

        // Schermata delle licenze (mostrata come un overlay)
        if (showLicenses) {
            // Stato per tenere traccia della scheda selezionata
            var tabIndex by remember { mutableStateOf(0) }
            val tabs = listOf("Dependencies", "App License", "Third Party License", "Readme")

            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1001f), // zIndex altissimo per stare sopra a tutto
                topBar = {
                    Column {
                        // Barra del titolo con pulsante indietro
                        TopAppBar(
                            title = { Text("Informazioni e Licenze") },
                            navigationIcon = {
                                IconButton(onClick = { showLicenses = false }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Indietro"
                                    )
                                }
                            }
                        )
                        // Barra delle schede (Tab)
                        TabRow(selectedTabIndex = tabIndex) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = tabIndex == index,
                                    onClick = { tabIndex = index },
                                    text = { Text(text = title) }
                                )
                            }
                        }
                    }
                }
            ) { paddingValues ->
                // Contenuto della scheda selezionata
                Column(modifier = Modifier.padding(paddingValues)) {
                    when (tabIndex) {
                        0 -> LibrariesContainer(
                            modifier = Modifier.fillMaxSize()
                        )
                        1 -> AssetTextView(
                            assetFileName = "LICENSE",
                            modifier = Modifier.fillMaxSize()
                        )
                        2 -> AssetTextView(
                            assetFileName = "THIRD_PARTY_LICENSES.md",
                            modifier = Modifier.fillMaxSize()
                        )
                        3 -> AssetTextView(
                            assetFileName = "README.md",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }


        // Pulsanti per la registrazione della traccia
        var isTracking by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .zIndex(2f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Pulsante per avviare/fermare la registrazione
            FloatingActionButton(
                onClick = {
                    isTracking = !isTracking
                    val intent = Intent(context, GpsTrackingService::class.java).apply {
                        action = if (isTracking) {
                            GpsTrackingService.ACTION_START
                        } else {
                            GpsTrackingService.ACTION_STOP
                        }
                    }
                    context.startService(intent)
                },
                containerColor = if (isTracking) Color.Red else Color.Green,
                shape = CircleShape
            ) {
                Icon(
                    painter = if (isTracking) {
                        painterResource(id = R.drawable.ic_stop) // TODO: Aggiungi un'icona di stop
                    } else {
                        painterResource(id = R.drawable.ic_play) // TODO: Aggiungi un'icona di play
                    },
                    contentDescription = if (isTracking) "Ferma registrazione" else "Avvia registrazione",
                    tint = Color.White
                )
            }

            // Pulsante per salvare la traccia (visibile solo durante la registrazione)
            if (isTracking) {
                FloatingActionButton(
                    onClick = {
                        // TODO: Implementare la logica per salvare la traccia in formato GPX
                    },
                    containerColor = Color.Blue,
                    shape = CircleShape
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_save), // Puoi usare un'icona di salvataggio esistente
                        contentDescription = "Salva traccia",
                        tint = Color.White
                    )
                }
            }
        }


        // Pulsante chiusura app
        var showExitDialog by remember { mutableStateOf(false) }

        //if (buttonsVisible) {
            FloatingActionButton(
                onClick = { showExitDialog = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = 30.dp, vertical = 60.dp)
                    .zIndex(2f),
                containerColor = Color.Black,
                shape = CircleShape
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.close),
                    contentDescription = "Chiudi app",
                    tint = Color.Unspecified
                )
            //}
        }


        // Dialog conferma chiusura app
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("Chiudi l'app") },
                text = { Text("Vuoi davvero chiudere l'applicazione?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showExitDialog = false
                            (context as? Activity)?.finishAffinity()
                        }
                    ) {
                        Text("Conferma")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text("Annulla")
                    }
                }
            )
        }

        // Dialog GPS assente
        if (showGpsDialog) {
            AlertDialog(
                onDismissRequest = { showGpsDialog = false },
                title = { Text("Segnale GPS assente") },
                text = {
                    Text(
                        "⚠️ Nessun segnale GPS rilevato.\n\n" +
                                "Assicurati che il GPS sia attivo e che il dispositivo abbia visibilità diretta del cielo."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showGpsDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

// Funzione helper per leggere e mostrare un file di testo dagli assets
@Composable
private fun AssetTextView(assetFileName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Legge il testo una sola volta e lo "ricorda"
    val text = remember(assetFileName) {
        try {
            context.assets.open(assetFileName).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "Errore nel caricamento del file: ${e.message}"
        }
    }

    // LazyColumn rende il testo scorrevole in modo efficiente
    LazyColumn(modifier = modifier.padding(16.dp)) {
        item {
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}