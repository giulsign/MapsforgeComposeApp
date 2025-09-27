/**
package com.example.mapsforgecomposeapp

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import androidx.core.content.edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.platform.LocalContext
import android.app.Activity




//copia direttamente italy.map nella cartella di destinazione
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


// Logica per aggiungere ID sequenziale al file json
fun getNextId(context: Context): Int {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val nextId = prefs.getInt("last_id", 0) + 1
    prefs.edit { putInt("last_id", nextId) }
    return nextId
}


// Funzione cancella i dati json con dialogo di conferma
fun clearSavedLocations(context: Context) {
    val file = File(context.filesDir, "locations.json")
    if (file.exists()) {
        file.writeText("[]") // JSON vuoto
    }
}


// Funzione che salva una posizione GPS su file JSON
fun saveLocationToJson(context: Context, location: Location) {

    val pointId = getNextId(context)  // 👈 prende l’ID progressivo salvato

    val file = File(context.filesDir, "locations.json")

    // Crea l'oggetto JSON per questa posizione
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        .format(Date())
    val hour = SimpleDateFormat("HH:mm:ss.S", Locale.getDefault())
        .format(Date()) // con decimi di secondo
    val locationJson = JSONObject().apply {
        put("id", pointId)
        put("latitude", location.latitude)
        put("longitude", location.longitude)
        put("altitude", location.altitude)
        put("date", date)
        put("hour", hour)
    }

    val jsonArray: JSONArray = if (file.exists() && file.length() > 0) {
        // Leggi contenuto esistente
        JSONArray(file.readText())
    } else {
        JSONArray()
    }

    // Aggiungi il nuovo punto
    jsonArray.put(locationJson)

    // Sovrascrivi il file con la nuova lista
    file.writeText(jsonArray.toString(2)) // 2 = indentazione leggibile
}


@Composable
fun MapsforgeMapComposable(
    context: Context,
    modifier: Modifier = Modifier,
    mapFileName: String = "italy.map"
) {
    // Stato posizione utente
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var showTable by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }


    // Lista delle posizioni salvate
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

    var userMarker: Marker? by remember { mutableStateOf(null) }

    // Funzione helper per creare il marker rosso
    fun createRedMarker(context: Context, latLong: LatLong): Marker {
        val drawable =
            ContextCompat.getDrawable(context, android.R.drawable.presence_online) // pallino rosso
        val bitmap = AndroidGraphicFactory.convertToBitmap(drawable)
        return Marker(latLong, bitmap, 0, -bitmap.height / 2)
    }

    // Controlla i permessi al primo avvio
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
            }

            else -> {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    // Preview layout in Android Studio
    /**if (LocalInspectionMode.current) {
    Box(
    modifier = modifier
    .fillMaxSize()
    .background(Color.Gray)
    .border(25.dp, Color.Black)
    ) {
    Text(
    "Anteprima mappa",
    modifier = Modifier.align(Alignment.Center)
    )

    // Pulsante MyLocation
    FloatingActionButton(
    onClick = {},
    modifier = Modifier
    .align(Alignment.BottomCenter)
    .padding(bottom = 100.dp)
    ) {
    Icon(
    imageVector = Icons.Filled.MyLocation,
    contentDescription = "Ritorna alla mia posizione"
    )
    }

    // Pulsanti Zoom in riga
    Row(
    modifier = Modifier
    .align(Alignment.BottomCenter)
    .padding(bottom = 30.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
    FloatingActionButton(onClick = {}) {
    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
    }
    FloatingActionButton(onClick = {}) {
    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
    }
    }


    // Salva il punto gps con metadati
    FloatingActionButton(
    onClick = {
    userLocation?.let { loc ->
    val text = "Lat: ${loc.latitude}, Lon: ${loc.longitude}, Alt: ${loc.altitude}"
    savedLocations = savedLocations + text
    }
    },
    modifier = Modifier
    .align(Alignment.TopStart) // a metà lato destro
    .padding(horizontal = 30.dp, vertical = 250.dp)
    ) {
    Icon(Icons.Filled.Save, contentDescription = "Save")
    }


    // Pulsante lista punti salvati
    FloatingActionButton(
    onClick = { showTable = true },
    modifier = Modifier
    .align(Alignment.TopStart)
    .padding(horizontal = 30.dp, vertical = 325.dp)
    ) {
    Icon(Icons.Filled.Search, contentDescription = "Search saved points")
    }


    // Pulsante cancella punti salvati con finestra di dialogo
    FloatingActionButton(
    onClick = { showTable = true },
    modifier = Modifier
    .align(Alignment.BottomStart)
    .padding(all = 30.dp)
    ) {
    Icon(Icons.Filled.Delete, contentDescription = "Delete points table")
    }

    // Pulsante chiusura app
    FloatingActionButton(
    onClick = { showTable = true },
    modifier = Modifier
    .align(Alignment.BottomEnd)
    .padding(30.dp)
    ) {
    Icon(
    imageVector = Icons.Filled.Close,
    contentDescription = "Chiudi applicazione"
    )
    }

    return
    }**/

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

    // Crea e mostra MapView
    Box(
        modifier = modifier
            .fillMaxSize()
            .border(25.dp, Color.Black)   // ✅ bordo attorno alla mappa
    ) {
        AndroidView(
            factory = {
                AndroidGraphicFactory.createInstance(context.applicationContext)
                val mapView = MapView(context).apply {
                    mapScaleBar.isVisible = false
                    // nessun controllo nativo
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

                // Posizione iniziale: fallback Milano
                val startLatLong = LatLong(45.4642, 9.19)
                mapView.model.mapViewPosition.setCenter(startLatLong)
                mapView.model.mapViewPosition.setZoomLevel(10.toByte())

                mapViewRef = mapView
                mapView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Pulsante MyLocation
        FloatingActionButton(
            onClick = {
                userLocation?.let { loc ->
                    val latLong = LatLong(loc.latitude, loc.longitude)
                    mapViewRef?.model?.mapViewPosition?.apply {
                        setCenter(latLong)
                        //setZoomLevel(15.toByte())
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = "Ritorna alla mia posizione"
            )
        }

        // 🔹 Pulsante laterale → salva posizione
        FloatingActionButton(
            onClick = {
                userLocation?.let { loc ->
                    saveLocationToJson(context, loc)
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart) // a metà lato destro
                .padding(horizontal = 30.dp, vertical = 250.dp)
        ) {
            Icon(Icons.Filled.Save, contentDescription = "Save")
        }

        // Pulsanti Zoom in riga
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 30.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FloatingActionButton(onClick = {
                mapViewRef?.model?.mapViewPosition?.zoomOut()
            }) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
            }
            FloatingActionButton(onClick = {
                mapViewRef?.model?.mapViewPosition?.zoomIn()
            }) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
            }
        }

        // Marker aggiornato con GPS
        LaunchedEffect(userLocation) {
            userLocation?.let { loc ->
                val latLong = LatLong(loc.latitude, loc.longitude)
                if (userMarker == null && mapViewRef != null) {
                    userMarker = createRedMarker(context, latLong)
                    mapViewRef?.layerManager?.layers?.add(userMarker)
                } else {
                    userMarker?.latLong = latLong
                }
                mapViewRef?.model?.mapViewPosition?.apply {
                    setCenter(latLong)
                    setZoomLevel(15.toByte())
                }
            }
        }

        // Visualizzazione tabella punti salvati
        FloatingActionButton(
            onClick = { showTable = true },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 30.dp, vertical = 325.dp)
        ) {
            Icon(Icons.Filled.Search, contentDescription = "Search saved points")
        }
        if (showTable) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.White.copy(alpha = 0.95f))
                    .padding(16.dp)
                    .align(Alignment.Center)
            ) {
                LocationsTableScreen(
                    context = context,
                    onBack = { showTable = false } // Azione per chiudere la tabella
                )
            }
        }

        // Pulsante cancella punti salvati con finestra di dialogo
        FloatingActionButton(
            onClick = {showConfirmDialog = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(all = 30.dp)
                .zIndex(2f)
        ) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete points table")
        }

        // Dialogo di conferma cancella punti salvati
        if (showConfirmDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            clearSavedLocations(context)
                            showTable = true
                            showConfirmDialog = false
                        }
                    ) {
                        androidx.compose.material3.Text("Conferma")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showConfirmDialog = false }) {
                        androidx.compose.material3.Text("Annulla")
                    }
                },
                title = { androidx.compose.material3.Text("Conferma eliminazione") },
                text = {
                    androidx.compose.material3.Text(
                        "Vuoi davvero cancellare tutti i punti salvati? Questa azione non può essere annullata."
                    )
                }
            )
        }

        // Pulsante chiusura app
        var showExitDialog by remember { mutableStateOf(false) }
        val context = LocalContext.current

        FloatingActionButton(
            onClick = { showExitDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(30.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Chiudi applicazione"
            )
        }

        // 🔔 Dialog di conferma chiusura app
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
                            // In alternativa, chiusura forzata:
                            // exitProcess(0)
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
    }
}





**/ //fine backup - pulsante centra in posizione, pulsanti zomm e bordo + pulsante save in json + pulsante chiusura
// pulsante cancella dati json + pulsante esportazione json(insviluppo)

package com.example.mapsforgecomposeapp

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import androidx.core.content.edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.platform.LocalContext
import android.app.Activity




//copia direttamente italy.map nella cartella di destinazione
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


// Logica per aggiungere ID sequenziale al file json
fun getNextId(context: Context): Int {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val nextId = prefs.getInt("last_id", 0) + 1
    prefs.edit { putInt("last_id", nextId) }
    return nextId
}


// Funzione cancella i dati json con dialogo di conferma
fun clearSavedLocations(context: Context) {
    val file = File(context.filesDir, "locations.json")
    if (file.exists()) {
        file.writeText("[]") // JSON vuoto
    }
}


// Funzione che salva una posizione GPS su file JSON
fun saveLocationToJson(context: Context, location: Location) {

    val pointId = getNextId(context)  // 👈 prende l’ID progressivo salvato

    val file = File(context.filesDir, "locations.json")

    // Crea l'oggetto JSON per questa posizione
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        .format(Date())
    val hour = SimpleDateFormat("HH:mm:ss.S", Locale.getDefault())
        .format(Date()) // con decimi di secondo
    val locationJson = JSONObject().apply {
        put("id", pointId)
        put("latitude", location.latitude)
        put("longitude", location.longitude)
        put("altitude", location.altitude)
        put("date", date)
        put("hour", hour)
    }

    val jsonArray: JSONArray = if (file.exists() && file.length() > 0) {
        // Leggi contenuto esistente
        JSONArray(file.readText())
    } else {
        JSONArray()
    }

    // Aggiungi il nuovo punto
    jsonArray.put(locationJson)

    // Sovrascrivi il file con la nuova lista
    file.writeText(jsonArray.toString(2)) // 2 = indentazione leggibile
}


@Composable
fun MapsforgeMapComposable(
    context: Context,
    modifier: Modifier = Modifier,
    mapFileName: String = "italy.map"
) {
    // Stato posizione utente
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var showTable by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }


    // Lista delle posizioni salvate
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

    var userMarker: Marker? by remember { mutableStateOf(null) }

    // Funzione helper per creare il marker rosso
    fun createRedMarker(context: Context, latLong: LatLong): Marker {
        val drawable =
            ContextCompat.getDrawable(context, android.R.drawable.presence_online) // pallino rosso
        val bitmap = AndroidGraphicFactory.convertToBitmap(drawable)
        return Marker(latLong, bitmap, 0, -bitmap.height / 2)
    }

    // Controlla i permessi al primo avvio
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
            }

            else -> {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    // Preview layout in Android Studio
    /**if (LocalInspectionMode.current) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Gray)
                .border(25.dp, Color.Black)
        ) {
            Text(
                "Anteprima mappa",
                modifier = Modifier.align(Alignment.Center)
            )

            // Pulsante MyLocation
            FloatingActionButton(
                onClick = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = "Ritorna alla mia posizione"
                )
            }

            // Pulsanti Zoom in riga
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FloatingActionButton(onClick = {}) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
                }
                FloatingActionButton(onClick = {}) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
                }
            }


            // Salva il punto gps con metadati
            FloatingActionButton(
                onClick = {
                    userLocation?.let { loc ->
                        val text = "Lat: ${loc.latitude}, Lon: ${loc.longitude}, Alt: ${loc.altitude}"
                        savedLocations = savedLocations + text
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart) // a metà lato destro
                    .padding(horizontal = 30.dp, vertical = 250.dp)
            ) {
                Icon(Icons.Filled.Save, contentDescription = "Save")
            }


            // Pulsante lista punti salvati
            FloatingActionButton(
                onClick = { showTable = true },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 30.dp, vertical = 325.dp)
            ) {
                Icon(Icons.Filled.Search, contentDescription = "Search saved points")
            }


            // Pulsante cancella punti salvati con finestra di dialogo
            FloatingActionButton(
                onClick = { showTable = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(all = 30.dp)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete points table")
            }

            // Pulsante chiusura app
            FloatingActionButton(
                onClick = { showTable = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Chiudi applicazione"
                )
        }

        return
    }**/

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

    // Crea e mostra MapView
    Box(
        modifier = modifier
            .fillMaxSize()
            .border(25.dp, Color.Black)   // ✅ bordo attorno alla mappa
    ) {
        AndroidView(
            factory = {
                AndroidGraphicFactory.createInstance(context.applicationContext)
                val mapView = MapView(context).apply {
                    mapScaleBar.isVisible = false
                    // nessun controllo nativo
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

                // Posizione iniziale: fallback Milano
                val startLatLong = LatLong(45.4642, 9.19)
                mapView.model.mapViewPosition.setCenter(startLatLong)
                mapView.model.mapViewPosition.setZoomLevel(10.toByte())

                mapViewRef = mapView
                mapView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Pulsante MyLocation
        FloatingActionButton(
            onClick = {
                userLocation?.let { loc ->
                    val latLong = LatLong(loc.latitude, loc.longitude)
                    mapViewRef?.model?.mapViewPosition?.apply {
                        setCenter(latLong)
                        //setZoomLevel(15.toByte())
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = "Ritorna alla mia posizione"
            )
        }

        // 🔹 Pulsante laterale → salva posizione
        FloatingActionButton(
            onClick = {
                userLocation?.let { loc ->
                    saveLocationToJson(context, loc)
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart) // a metà lato destro
                .padding(horizontal = 30.dp, vertical = 250.dp)
        ) {
            Icon(Icons.Filled.Save, contentDescription = "Save")
        }

        // Pulsanti Zoom in riga
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 30.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FloatingActionButton(onClick = {
                mapViewRef?.model?.mapViewPosition?.zoomOut()
            }) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
            }
            FloatingActionButton(onClick = {
                mapViewRef?.model?.mapViewPosition?.zoomIn()
            }) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
            }
        }

        // Marker aggiornato con GPS
        LaunchedEffect(userLocation) {
            userLocation?.let { loc ->
                val latLong = LatLong(loc.latitude, loc.longitude)
                if (userMarker == null && mapViewRef != null) {
                    userMarker = createRedMarker(context, latLong)
                    mapViewRef?.layerManager?.layers?.add(userMarker)
                } else {
                    userMarker?.latLong = latLong
                }
                mapViewRef?.model?.mapViewPosition?.apply {
                    setCenter(latLong)
                    setZoomLevel(15.toByte())
                }
            }
        }

        // Visualizzazione tabella punti salvati
        FloatingActionButton(
            onClick = { showTable = true },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 30.dp, vertical = 325.dp)
        ) {
            Icon(Icons.Filled.Search, contentDescription = "Search saved points")
        }
        if (showTable) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.White.copy(alpha = 0.95f))
                    .padding(16.dp)
                    .align(Alignment.Center)
            ) {
                LocationsTableScreen(
                    context = context,
                    onBack = { showTable = false } // Azione per chiudere la tabella
                )
            }
        }

        // Pulsante cancella punti salvati con finestra di dialogo
        FloatingActionButton(
            onClick = {showConfirmDialog = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(all = 30.dp)
                .zIndex(2f)
        ) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete points table")
        }

        // Dialogo di conferma cancella punti salvati
        if (showConfirmDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            clearSavedLocations(context)
                            showTable = true
                            showConfirmDialog = false
                        }
                    ) {
                        androidx.compose.material3.Text("Conferma")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showConfirmDialog = false }) {
                        androidx.compose.material3.Text("Annulla")
                    }
                },
                title = { androidx.compose.material3.Text("Conferma eliminazione") },
                text = {
                    androidx.compose.material3.Text(
                        "Vuoi davvero cancellare tutti i punti salvati? Questa azione non può essere annullata."
                    )
                }
            )
        }

        // Pulsante chiusura app
        var showExitDialog by remember { mutableStateOf(false) }
        val context = LocalContext.current

        FloatingActionButton(
            onClick = { showExitDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(30.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Chiudi applicazione"
            )
        }

        // 🔔 Dialog di conferma chiusura app
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
                            // In alternativa, chiusura forzata:
                            // exitProcess(0)
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
        }
}


