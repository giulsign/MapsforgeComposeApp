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
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.core.graphics.Style
import androidx.compose.runtime.collectAsState
import org.mapsforge.core.graphics.Cap
import org.mapsforge.core.graphics.Join
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import it.fourSTL.PositionMarker.ui.theme.MyCustomFont
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import it.fourSTL.PositionMarker.ui.theme.Purple40


//  memorizzare metadati persistenti
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
        val drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_marker_red, null)
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


// Composable per il pulsante categoria verticale
@Composable
fun VerticalCategoryButton(
    text: String,
    alignment: Alignment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White
) {
    Box(
        modifier = modifier
            .fillMaxWidth(0.5f)
            //.fillMaxHeight(0.2f)
            .height(70.dp)
            .background(Color(0xB3E0E0E0), shape = RectangleShape)
            .border((1.dp), Color(0xFF99CCFF))
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontFamily = MyCustomFont,
            fontSize = 20.sp,
            color = (Purple40),
            fontWeight = FontWeight.Bold
        )
    }
}

    // Composable per il pulsante categoria orizzontale
    @Composable
    fun HorizontalCategoryButton(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        backgroundColor: Color = Color.White
    ) {
        Box(
            modifier = modifier
                .height(50.dp)
                .width(120.dp)
                .background(backgroundColor, shape = MaterialTheme.shapes.medium)
                .clickable(onClick = onClick)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontFamily = MyCustomFont,
                fontWeight = FontWeight.Bold
            )
        }
    }

    // Composable per il menu dropdown
    @Composable
    fun CategoryMenu(
        title: String,
        items: List<Pair<String, () -> Unit>>,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items.forEach { (itemText, itemAction) ->
                        Button(
                            onClick = {
                                itemAction()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(itemText)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Chiudi")
                }
            }
        )
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
        var buttonsVisible by remember { mutableStateOf(false) }
        var isInitialLocationSet by remember { mutableStateOf(false) }
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

        var savedLocations by remember { mutableStateOf(listOf<String>()) }
        val fusedLocationClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)

        // 🔹 NUOVO: Stati per il tracciato in tempo reale dal servizio
        val realTimeTrackPoints by GpsTrackingService.trackPointsFlow.collectAsState()
        var realTimePolyline by remember { mutableStateOf<Polyline?>(null) }
        var isTracking by remember { mutableStateOf(false) }
        var fileName by remember { mutableStateOf("") }
        var showSaveDialog by remember { mutableStateOf(false) }


        // 🔹 Raccoglie i punti della traccia dal servizio come uno stato
        val trackPoints by GpsTrackingService.trackPointsFlow.collectAsState()

        // 🔹 Stati per il percorso GPX caricato
        var loadedGpxTrack by remember { mutableStateOf<List<LatLong>>(emptyList()) }
        var loadedGpxPolyline by remember { mutableStateOf<Polyline?>(null) }
        var showLoadedGpxTrack by remember { mutableStateOf(true) }

        // Stati per i menu delle categorie
        var showPosizioneMenu by remember { mutableStateOf(false) }
        var showPartenzaMenu by remember { mutableStateOf(false) }
        var showDatiMenu by remember { mutableStateOf(false) }
        var showTracciaMenu by remember { mutableStateOf(false) }


        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp
        val screenHeight = configuration.screenHeightDp.dp


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


        // 🔹 NUOVO: Launcher per selezionare un file GPX dal dispositivo
        val gpxFilePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
            onResult = { uri: Uri? ->
                if (uri != null) {
                    val points = GpxParser.parse(context, uri)
                    if (points.isNotEmpty()) {
                        loadedGpxTrack = points
                        showLoadedGpxTrack = true
                        Toast.makeText(
                            context,
                            "Percorso GPX caricato con ${points.size} punti",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Errore: Impossibile leggere il file GPX o file vuoto",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )


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
            //val drawable = ContextCompat.getDrawable(context, R.drawable.presence_online)
            //val bitmap = AndroidGraphicFactory.convertToBitmap(drawable)
            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_marker_red)
            val bitmap = AndroidGraphicFactory.convertToBitmap(drawable)
            //return Marker(latLong, bitmap, 0, -bitmap.height / 2)
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

                    val locationCallback =
                        object : com.google.android.gms.location.LocationCallback() {
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
                .padding(WindowInsets.systemBars.asPaddingValues()) // ⬅️ AGGIUNGI QUESTA RIGA
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

                    // Posizione iniziale di fallback: Milano. Corretto.
                    val startLatLong = LatLong(45.4642, 9.19)
                    mapView.model.mapViewPosition.setCenter(startLatLong)
                    mapView.model.mapViewPosition.setZoomLevel(15.toByte())

                    // Crea il marker iniziale a Milano, che poi verrà spostato.
                    userMarker = createRedMarker(context, startLatLong)
                    mapView.layerManager.layers.add(userMarker)

                    mapViewRef = mapView
                    mapView
                },
                update = { mapView ->
                    userLocation?.let { loc ->
                        val latLong = LatLong(loc.latitude, loc.longitude)

                        // Sposta la posizione del marker esistente.
                        userMarker?.latLong = latLong

                        // Se non abbiamo ancora impostato la posizione iniziale,
                        // centra la mappa sulla posizione GPS e imposta il flag a true.
                        if (!isInitialLocationSet) {
                            mapView.model.mapViewPosition.setCenter(latLong)
                            mapView.model.mapViewPosition.setZoomLevel(15.toByte())
                            isInitialLocationSet = true
                        }

                        // Forza il ridisegno della mappa.
                        mapView.invalidate()
                    }
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
                containerColor = Color(0xB3E0E0E0),
                contentColor = Color.Transparent,
                shape = RectangleShape,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.my_location),
                    contentDescription = "Vai alla mia posizione",
                    tint = Color.Unspecified
                )
            }

            // 🔹 Pulsante salva posizione
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
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 30.dp, vertical = 100.dp)
                    .zIndex(2f),
                containerColor = Color(0xB3E0E0E0),
                contentColor = Color.Transparent,
                shape = RectangleShape,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.save),
                    contentDescription = "Salva",
                    tint = Color.Unspecified
                )
            }

            // Pulsante salva posizione auto
            FloatingActionButton(
                onClick = {
                    userLocation?.let { loc ->
                        saveLocationToJsonAuto(context, loc)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 30.dp, vertical = 100.dp)
                    .zIndex(2f),
                containerColor = Color(0xB3E0E0E0),
                contentColor = Color.Transparent,
                shape = RectangleShape,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.save_car),
                    contentDescription = "Salva posizione partenza",
                    tint = Color.Unspecified
                )
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
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 30.dp)
                    .background(Color.Unspecified),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FloatingActionButton(
                    onClick = { mapViewRef?.model?.mapViewPosition?.zoomOut() },
                    containerColor = Color(0xB3E0E0E0),
                    contentColor = Color.Transparent,
                    shape = RectangleShape,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.zoom_out),
                        contentDescription = "Zoom out",
                        tint = Color.Unspecified
                    )
                }
                FloatingActionButton(
                    onClick = { mapViewRef?.model?.mapViewPosition?.zoomIn() },
                    containerColor = Color(0xB3E0E0E0),
                    contentColor = Color.Transparent,
                    shape = RectangleShape,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.zoom_in),
                        contentDescription = "Zoom in",
                        tint = Color.Unspecified
                    )
                }
            }


            // ========== CATEGORIA POSIZIONE (Bordo Sinistro) ==========
            if (buttonsVisible) {
                VerticalCategoryButton(
                    text = "METADATAS MENU",
                    alignment = Alignment.TopStart,
                    onClick = { showPosizioneMenu = true },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(vertical = 144.dp)
                        .zIndex(2f)
                )
            }

        if (showPosizioneMenu) {
            CategoryMenu(
                title = "Metadatas Menu",
                items = listOf(
                    "Select metadatas to save" to {
                        showSelectionScreen = true
                    },
                    "Save GPS point with metadatas" to {
                        userLocation?.let { loc ->
                            persistentMetadata = loadPersistentMetadata(context)
                            val finalMetadata = persistentMetadata + selectedMetadata
                            saveLocationToJson(context, loc, finalMetadata)
                            selectedMetadata = emptyMap()
                        }
                    },
                    "Show metadatas point saved" to {
                        showTable = true
                    },
                    "Reset metadatas selecions" to {
                        clearAllPersistentData(context)
                        persistentSelectionsState.value = mutableSetOf()
                        persistentMetadata = emptyMap()
                        Toast.makeText(context, "None persistent metadatas selctions", Toast.LENGTH_SHORT).show()
                    },
                    "Export Gps points to JSON" to {
                        exportJsonToDownload(context)
                    },
                    "Delete saved points" to {
                        showConfirmDialog = true
                    },
                ),
                onDismiss = { showPosizioneMenu = false }
            )
        }


// ========== CATEGORIA PERCORSI GPS (Bordo Sinistro) ==========
            if (buttonsVisible) {
                VerticalCategoryButton(
                    text = "GPS TRACK MENU",
                    alignment = Alignment.TopStart,
                    onClick = { showTracciaMenu = true },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(vertical = (144.dp + 70.dp + 2.dp))
                        .zIndex(2f)
                )
            }

            if (showTracciaMenu) {
                val trackingItems = mutableListOf<Pair<String, () -> Unit>>()

                // Avvia/Ferma registrazione
                trackingItems.add(
                    (if (isTracking) "Stop Gps Tracking" else "Start GPS Tracking") to {
                        isTracking = !isTracking
                        val intent = Intent(context, GpsTrackingService::class.java).apply {
                            action = if (isTracking) {
                                GpsTrackingService.ACTION_START
                            } else {
                                GpsTrackingService.ACTION_STOP
                            }
                        }
                        context.startService(intent)
                    }
                )

                // Salva traccia (solo se in registrazione)
                if (isTracking) {
                    trackingItems.add(
                        "Save GPX Track" to {
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            fileName = "track_$timestamp"
                            showSaveDialog = true
                        }
                    )
                }

                // Carica traccia
                trackingItems.add(
                    "Load GPX Track" to {gpxFilePickerLauncher.launch("*/*")}
                    )

                // Mostra/Nascondi traccia caricata (solo se esiste)
                if (loadedGpxTrack.isNotEmpty()) {
                    trackingItems.add(
                        (if (showLoadedGpxTrack) "Hide GPX track uploaded" else "Show GPX Track Uploaded") to {
                            showLoadedGpxTrack = !showLoadedGpxTrack
                        }
                    )

                }

                CategoryMenu(
                    title = "Gps track Menu",
                    items = trackingItems,
                    onDismiss = { showTracciaMenu = false }
                )
            }


                // ========== CATEGORIA PARTENZA (Bordo Destro) ==========
                            if (buttonsVisible) {
                                VerticalCategoryButton(
                                    text = "START MENU",
                                    alignment = Alignment.TopEnd,
                                    onClick = { showPartenzaMenu = true },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(vertical = 144.dp)
                                        .zIndex(2f)
                                )
                            }

                        if (showPartenzaMenu) {
                            CategoryMenu(
                                title = "Start point menu",
                                items = listOf(
                                    "Save start point" to {
                                        userLocation?.let { loc ->
                                            saveLocationToJsonAuto(context, loc)
                                        }
                                    },
                                    (if (showCarMarker) "Hide start point" else "Show start point") to {
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

                                                            userLocation?.let { currentLoc ->
                                                                val startLocation = Location("").apply {
                                                                    setLatitude(latitude)
                                                                    setLongitude(longitude)
                                                                }
                                                                val distance = currentLoc.distanceTo(startLocation) / 1000f
                                                                Toast.makeText(
                                                                    context,
                                                                    String.format(
                                                                        Locale.getDefault(),
                                                                        "📍 Start point. Distance: %.2f km",
                                                                        distance
                                                                    ),
                                                                    Toast.LENGTH_LONG
                                                                ).show()
                                                            } ?: run {
                                                                Toast.makeText(
                                                                    context,
                                                                    "📍 Start point.",
                                                                    Toast.LENGTH_LONG
                                                                ).show()
                                                            }

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
                                                        Toast.makeText(context, "❌ Start point reading error", Toast.LENGTH_LONG).show()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "⚠️ No start point saved.", Toast.LENGTH_LONG).show()
                                                }
                                            } else {
                                                carMarker?.let { marker ->
                                                    map.layerManager.layers.remove(marker)
                                                }
                                                carMarker = null
                                                showCarMarker = false
                                                Toast.makeText(context, "Start point removed.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    "Remove start point" to {
                                        showConfirmDialogAuto = true
                                    }
                                ),
                                onDismiss = { showPartenzaMenu = false }
                            )
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
                                        mapViewRef?.invalidate()
                                    }
                                }
                            }


                            // 🔹 NUOVO: LaunchedEffect per disegnare la traccia BLU in tempo reale
                            LaunchedEffect(realTimeTrackPoints, mapViewRef) {
                                val mapView = mapViewRef ?: return@LaunchedEffect
                                realTimePolyline?.let { mapView.layerManager.layers.remove(it) }
                                if (realTimeTrackPoints.size > 1) {
                                    val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
                                        setStyle(Style.STROKE)
                                        color = android.graphics.Color.BLUE
                                        strokeWidth = 12f
                                        setStrokeJoin(Join.ROUND)
                                        setStrokeCap(Cap.ROUND)
                                    }
                                    val newPolyline = Polyline(paint, AndroidGraphicFactory.INSTANCE).apply {
                                        realTimeTrackPoints.forEach { location ->
                                            addPoint(LatLong(location.latitude, location.longitude))
                                        }
                                    }
                                    mapView.layerManager.layers.add(newPolyline)
                                    realTimePolyline = newPolyline
                                }
                            }


                            // 🔹 NUOVO: LaunchedEffect per disegnare la traccia VIOLA caricata da GPX
                            LaunchedEffect(loadedGpxTrack, showLoadedGpxTrack, mapViewRef) {
                                val mapView = mapViewRef ?: return@LaunchedEffect
                                loadedGpxPolyline?.let { mapView.layerManager.layers.remove(it) }
                                if (showLoadedGpxTrack && loadedGpxTrack.isNotEmpty()) {
                                    val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
                                        setStyle(Style.STROKE)
                                        color = android.graphics.Color.MAGENTA
                                        strokeWidth = 10f
                                        setStrokeJoin(Join.ROUND)
                                        setStrokeCap(Cap.ROUND)
                                    }
                                    val newPolyline = Polyline(paint, AndroidGraphicFactory.INSTANCE).apply {
                                        loadedGpxTrack.forEach { point -> addPoint(point) }
                                    }
                                    // Inserisce il layer sotto il marker utente per non coprirlo
                                    val layerIndex = if (mapView.layerManager.layers.size() > 0) {
                                        mapView.layerManager.layers.size() - 1
                                    } else {
                                        0
                                    }
                                    mapView.layerManager.layers.add(layerIndex, newPolyline)
                                    loadedGpxPolyline = newPolyline
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
                                                filteredMarkers.forEach {
                                                    mapViewRef?.layerManager?.layers?.remove(
                                                        it
                                                    )
                                                }

                                                val markers = locations.map { loc ->
                                                    val latLong = LatLong(loc.latitude, loc.longitude)
                                                    val drawable = ResourcesCompat.getDrawable(
                                                        context.resources,
                                                        R.drawable.ic_marker_blue,
                                                        null
                                                    )
                                                    val markerBitmap =
                                                        AndroidGraphicFactory.convertToBitmap(drawable)
                                                    Marker(latLong, markerBitmap, 0, -markerBitmap.height / 2)
                                                }

                                                markers.forEach { mapViewRef?.layerManager?.layers?.add(it) }
                                                filteredMarkers = markers

                                                if (locations.isNotEmpty()) {
                                                    val firstLocation = locations.first()
                                                    val latLong =
                                                        LatLong(firstLocation.latitude, firstLocation.longitude)
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

                                                        // Calcola la distanza
                                                        userLocation?.let { currentLoc ->
                                                            val startLocation = Location("").apply {
                                                                setLatitude(latitude)
                                                                setLongitude(longitude)
                                                            }
                                                            val distance =
                                                                currentLoc.distanceTo(startLocation) / 1000f // in km
                                                            Toast.makeText(
                                                                context,
                                                                String.format(
                                                                    Locale.getDefault(),
                                                                    "📍 Punto di partenza visualizzato. Distanza: %.2f km",
                                                                    distance
                                                                ),
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        } ?: run {
                                                            Toast.makeText(
                                                                context,
                                                                "📍 Punto di partenza visualizzato.",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        }


                                                        val drawable = ResourcesCompat.getDrawable(
                                                            context.resources,
                                                            R.drawable.ic_marker,
                                                            null
                                                        )
                                                        val markerBitmap =
                                                            AndroidGraphicFactory.convertToBitmap(drawable)
                                                        carMarker = Marker(
                                                            geoPoint,
                                                            markerBitmap,
                                                            0,
                                                            -markerBitmap.height / 2
                                                        )
                                                        map.layerManager.layers.add(carMarker)
                                                        map.model.mapViewPosition.setCenter(geoPoint)
                                                        showCarMarker = true
                                                    }
                                                } catch (e: Exception) {
                                                    Toast.makeText(
                                                        context,
                                                        "❌ Errore lettura punto auto",
                                                        Toast.LENGTH_LONG
                                                    )
                                                        .show()
                                                }
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "⚠️ Nessun punto auto salvato.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        } else {
                                            carMarker?.let { marker ->
                                                map.layerManager.layers.remove(marker)
                                            }
                                            carMarker = null
                                            showCarMarker = false
                                            Toast.makeText(
                                                context,
                                                "Punto di partenza nascosto.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(horizontal = 30.dp, vertical = 30.dp)
                                    .zIndex(2f),
                                containerColor = Color(0xB3E0E0E0),
                                contentColor = Color.Transparent,
                                shape = RectangleShape,
                                elevation = FloatingActionButtonDefaults.elevation(
                                    defaultElevation = 0.dp,
                                    pressedElevation = 0.dp,
                                    focusedElevation = 0.dp,
                                    hoveredElevation = 0.dp
                                )
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.show_start),
                                    contentDescription = "Mostra punto partenza",
                                    tint = Color.Unspecified
                                )
                            }


                            // 🔹 Pulsante apertura SelectionScreen
                            FloatingActionButton(
                                onClick = { showSelectionScreen = true },
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 30.dp, vertical = 30.dp)
                                    .zIndex(2f),
                                containerColor = Color(0xB3E0E0E0),
                                contentColor = Color.Transparent,
                                shape = RectangleShape,
                                elevation = FloatingActionButtonDefaults.elevation(
                                    defaultElevation = 0.dp,
                                    pressedElevation = 0.dp,
                                    focusedElevation = 0.dp,
                                    hoveredElevation = 0.dp
                                )
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.list),
                                    contentDescription = "Apri tabella selezione",
                                    tint = Color.Unspecified
                                )
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


                            // Pulsante toggle visibilità pulsanti
                            Button(
                                onClick = {
                                    buttonsVisible = !buttonsVisible
                                },
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(vertical = 72.dp)
                                    .width(screenWidth * 1.00f)
                                    .height(70.dp)
                                    .zIndex(2f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xB3E0E0E0)
                                ),
                                shape = RectangleShape,
                                border = BorderStroke(1.dp, Color(0xFF99CCFF))
                            ) {
                                if (buttonsVisible)
                                    Text(
                                        text = "Hide buttons",
                                        color = Color.Red,
                                        fontSize = 27.sp,
                                        textAlign = TextAlign.Center,
                                        fontFamily = MyCustomFont,
                                        fontWeight = FontWeight.Bold
                                    )
                                else
                                    Text(
                                        text = "Show buttons",
                                        color = Color.Green,
                                        fontSize = 27.sp,
                                        textAlign = TextAlign.Center,
                                        fontFamily = MyCustomFont,
                                        fontWeight = FontWeight.Bold
                                    )
                            }


                            // Pulsante per le licenze
                            Button(
                                onClick = { showLicenses = true },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    //.padding(horizontal = 30.dp, vertical = 60.dp)
                                    .width(screenWidth * 0.5f)
                                    .height(70.dp)
                                    .zIndex(2f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xB3E0E0E0)
                                ),
                                shape = RectangleShape,
                                border = BorderStroke(1.dp, Color(0xFF99CCFF))
                            ) {
                                Text(
                                    text = "Licenses",
                                    color = Color.Blue,
                                    fontSize = 27.sp,
                                    textAlign = TextAlign.Center,
                                    fontFamily = MyCustomFont,
                                    fontWeight = FontWeight.Bold
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


                            // Dialog per il salvataggio del file
                            if (showSaveDialog) {
                                AlertDialog(
                                    onDismissRequest = { showSaveDialog = false },
                                    title = { Text("Salva Traccia GPX") },
                                    text = {
                                        // Campo di testo per inserire il nome del file
                                        androidx.compose.material3.TextField(
                                            value = fileName,
                                            onValueChange = { fileName = it },
                                            label = { Text("Nome del file") },
                                            singleLine = true
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                val intent = Intent(context, GpsTrackingService::class.java).apply {
                                                    action = GpsTrackingService.ACTION_SAVE
                                                    putExtra(GpsTrackingService.EXTRA_FILENAME, fileName)
                                                }
                                                context.startService(intent)
                                                showSaveDialog = false
                                            }
                                        ) {
                                            Text("Salva")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showSaveDialog = false }) {
                                            Text("Annulla")
                                        }
                                    }
                                )
                            }


                            // 🔹 NUOVO: Pulsante per caricare un percorso GPX
                            if (buttonsVisible) {
                                FloatingActionButton(
                                    onClick = { gpxFilePickerLauncher.launch("*/*") },
                    modifier = Modifier
                        .align(Alignment.BottomEnd) // Puoi cambiare questa posizione
                        .padding(
                            horizontal = 30.dp,
                            vertical = 50.dp
                        ) // Puoi cambiare questa posizione
                        .zIndex(2f),
                    containerColor = Color(0xB3E0E0E0),
                    contentColor = Color.Transparent,
                    shape = RectangleShape,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.file_open), // Aggiungi icona 'file_open'
                        contentDescription = "Carica percorso GPX",
                        tint = Color.White
                    )
                }
            }

            // 🔹 NUOVO: Pulsante per mostrare/nascondere il percorso GPX caricato
            if (loadedGpxTrack.isNotEmpty() && buttonsVisible) {
                FloatingActionButton(
                    onClick = { showLoadedGpxTrack = !showLoadedGpxTrack },
                    modifier = Modifier
                        .align(Alignment.BottomEnd) // Puoi cambiare questa posizione
                        .padding(
                            horizontal = 30.dp,
                            vertical = 125.dp
                        ) // Puoi cambiare questa posizione
                        .zIndex(2f),
                    containerColor = Color(0xB3E0E0E0),
                    contentColor = Color.Transparent,
                    shape = RectangleShape,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    )
                ) {
                    Icon(
                        painter = if (showLoadedGpxTrack) {
                            painterResource(id = R.drawable.ic_visibility_off) // Aggiungi icona
                        } else {
                            painterResource(id = R.drawable.ic_visibility) // Aggiungi icona
                        },
                        contentDescription = "Mostra/Nascondi percorso caricato",
                        tint = Color.Black
                    )
                }
            }


            // Pulsante chiusura app
            var showExitDialog by remember { mutableStateOf(false) }

            //if (buttonsVisible) {
            Button(
                onClick = { showExitDialog = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    //.padding(horizontal = 30.dp, vertical = 60.dp)
                    .width(screenWidth * 0.5f)
                    .height(70.dp)
                    .zIndex(2f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xB3E0E0E0)
                ),
                shape = RectangleShape,
                border = BorderStroke(1.dp, Color(0xFF99CCFF))
            ) {
                Text(
                    text = "Close App",
                    color = Color.Red,
                    fontSize = 27.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = MyCustomFont,
                    fontWeight = FontWeight.Bold
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