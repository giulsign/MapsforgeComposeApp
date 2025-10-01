/* Applicare le modifiche come nel file tct in desktop salvato*/
// pulsante cancella dati json + pulsante salva posizione auto(partenza) + pulsante esportazione json(insviluppo)

package it.fourSTL.PositionMarker

import it.fourSTL.PositionMarker.R
import android.Manifest
//import android.R
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
import android.os.Environment
import android.widget.Toast
import java.io.FileInputStream
import java.io.FileOutputStream
import org.mapsforge.core.model.LatLong as MapsforgeLatLong
import androidx.core.content.res.ResourcesCompat
import java.util.*


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

// Mostra la posizione di partenza sulla mappa a richiesta
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

        // Recupera il primo punto salvato
        val locationJson = jsonArray.getJSONObject(0)
        val latitude = locationJson.getDouble("latitude")
        val longitude = locationJson.getDouble("longitude")
        val date = locationJson.getString("date")
        val hour = locationJson.getString("hour")

        // Coordinate Mapsforge (long, lat)
        val geoPoint = LatLong(latitude, longitude)

        // Carica un'icona per il marker (es. da drawable)
        val drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_marker, null)
        val markerBitmap = AndroidGraphicFactory.convertToBitmap(drawable)

        val marker = Marker(geoPoint, markerBitmap, 0, -markerBitmap.height / 2)

        // Layer dei marker
        val layer = map.layerManager.layers
        layer.add(marker)

        // Centra e zooma la mappa sul punto
        map.model.mapViewPosition.setCenter(geoPoint)
        //map.model.mapViewPosition.setZoomLevel(17.toByte())

        Toast.makeText(context, "📍 Punto di partenza visualizzato sulla mappa", Toast.LENGTH_LONG).show()

    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "❌ Errore nella lettura del punto salvato.", Toast.LENGTH_LONG).show()
    }
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
    Toast.makeText(context, "Dati tabella JSON cancellati", Toast.LENGTH_LONG).show()
}


// Funzione cancella i dati json posizione auto
fun clearSavedLocationsAuto(context: Context) {
    val file = File(context.filesDir, "auto_locations.json")
    if (file.exists()) {
        file.writeText("[]") // JSON vuoto
    }
    Toast.makeText(context, "Dati posizione di partenza cancellati", Toast.LENGTH_LONG).show()
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

    Toast.makeText(context, "Nuovo punto aggiunto a tabella JSON", Toast.LENGTH_LONG).show()
}


// Salva la posizione di partenza su file JSON e non consente la sovrascrittura
fun saveLocationToJsonAuto(context: Context, location: Location) {
    val file = File(context.filesDir, "auto_locations.json")

    // Se esiste già un punto salvato, blocca il salvataggio
    if (file.exists() && file.length() > 0) {
        try {
            val jsonArray = JSONArray(file.readText())
            if (jsonArray.length() > 0) {
                Toast.makeText(
                    context,
                    "⚠️ Esiste già un punto salvato. Eliminare prima di aggiungerne un altro.",
                    Toast.LENGTH_LONG
                ).show()
                return // blocca qui
            }
        } catch (e: Exception) {
            // se errore nel file, possiamo resettarlo
            file.delete()
        }
    }

    // Genera nuovo ID progressivo
    val pointId = getNextId(context)

    // Crea l'oggetto JSON per questa posizione
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val hour = SimpleDateFormat("HH:mm:ss.S", Locale.getDefault()).format(Date()) // con decimi di secondo

    val locationJson = JSONObject().apply {
        put("id", pointId)
        put("latitude", location.latitude)
        put("longitude", location.longitude)
        put("altitude", location.altitude)
        put("date", date)
        put("hour", hour)
    }

    // Crea un nuovo JSONArray e aggiunge il punto
    val jsonArray = JSONArray().apply { put(locationJson) }

    // Scrive il file
    file.writeText(jsonArray.toString(2))

    Toast.makeText(
        context,
        "✅ Nuovo punto di partenza salvato.",
        Toast.LENGTH_LONG
    ).show()
}


// funzione esporta punti salvati in formato json in cartella download
fun exportJsonToDownload(context: Context) {
    try {
        // File sorgente nella directory interna dell'app
        val sourceFile = File(context.filesDir, "locations.json")

        if (!sourceFile.exists()) {
            Toast.makeText(context, "Nessun file JSON trovato", Toast.LENGTH_SHORT).show()
            return
        }

        // Prepara nome file con timestamp
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val fileName = "fourSTLPositionMarker_locations_$timestamp.json"

        // Directory di destinazione (Download pubblica)
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadDir.exists()) downloadDir.mkdirs()

        val destFile = File(downloadDir, fileName)

        // Copia del file
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


//Main composable
@Composable
fun fourSTLPositionMarkerComposable(
    context: Context,
    modifier: Modifier = Modifier,
    mapFileName: String = "italy.map"
) {
    // Stato posizione utente
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var showTable by remember { mutableStateOf(false) }              // mostra tabella punti
    var showTableAuto by remember { mutableStateOf(false) }          // mostra tabella auto
    var showConfirmDialog by remember { mutableStateOf(false) }      // conferma cancellazione punti
    var showConfirmDialogAuto by remember { mutableStateOf(false) }  // conferma cancellazione auto
    var showConfirmMessage by remember { mutableStateOf(false) }     // messaggio finale dopo cancellazione punti
    var showConfirmMessageAuto by remember { mutableStateOf(false) } // messaggio finale dopo cancellazione auto
    var showGpsDialog by remember { mutableStateOf(false) }          // avviso GPS assente
    var carMarker: Marker? by remember { mutableStateOf(null) }
    var showCarMarker by remember { mutableStateOf(false) }

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
            ContextCompat.getDrawable(context, R.drawable.presence_online) // pallino rosso
        val bitmap = AndroidGraphicFactory.convertToBitmap(drawable)
        return Marker(latLong, bitmap, 0, -bitmap.height / 2)
    }


    //Nuovo launched effect per gestire i permessi e aggiornare in ocntinuo la posizione del marker
    LaunchedEffect(Unit) {
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                hasLocationPermission = true

                // Prima posizione nota (può essere null)
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        userLocation = location
                    }
                }

                // 🔁 Aggiornamento continuo in tempo reale
                val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                    com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                    2000 // ogni 2 secondi
                ).build()

                val locationCallback = object : com.google.android.gms.location.LocationCallback() {
                    override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                        result.lastLocation?.let { loc ->
                            userLocation = loc // aggiorna marker in tempo reale
                        }
                    }
                }

                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    android.os.Looper.getMainLooper()
                )

                // 🔔 Timeout: se dopo 20 secondi userLocation è ancora null, mostra dialog
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
                //mapView.model.mapViewPosition.setZoomLevel(10.toByte())

                userMarker = createRedMarker(context, startLatLong)
                mapView.layerManager.layers.add(userMarker)


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
                        setZoomLevel(15.toByte())
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
                .zIndex(2f)
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
                .zIndex(2f)
        ) {
            Icon(Icons.Filled.Save, contentDescription = "Save")
        }


        // 🔹 Pulsante laterale → salva posizione automobile / punto partenza
        FloatingActionButton(
            onClick = {
                userLocation?.let { loc ->
                    saveLocationToJsonAuto(context, loc)
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd) // a metà lato destro
                .padding(horizontal = 30.dp, vertical = 250.dp)
                .zIndex(2f)
        ) {
            Icon(Icons.Filled.DirectionsCarFilled, contentDescription = "Auto")
        }


        FloatingActionButton(
            onClick = { showConfirmDialog = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 30.dp, vertical = 30.dp)
                .zIndex(2f)
        ) {
            Icon(Icons.Filled.Delete, contentDescription = "Cancella tabella punti")
        }

        // 🔹 Dialogo conferma cancellazione punti salvati
        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            clearSavedLocations(context)
                            showConfirmDialog = false
                            showConfirmMessage = true // ✅ Mostra messaggio finale
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
                    Text(
                        "Vuoi davvero cancellare la tabella dei punti salvati? Questa azione non può essere annullata."
                    )
                }
            )
        }

        // 🔹 Dialogo messaggio finale dopo cancellazione
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


        // pulsante esportazione dati json salvati
        val contextData = LocalContext.current

        FloatingActionButton(
                onClick = { exportJsonToDownload(contextData) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 30.dp, vertical = 350.dp)
                    .zIndex(2f)
            ) {
                Icon(
                    imageVector = Icons.Default.FileCopy,
                    contentDescription = "Esporta tabella punti in formato JSON"
                )
            }


        // 🔹 Pulsante cancella posizione auto
        FloatingActionButton(
            onClick = { showConfirmDialogAuto = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 30.dp, vertical = 350.dp)
                .zIndex(2f)
        ) {
            Icon(Icons.Filled.Delete, contentDescription = "Cancella posizione auto")
        }

        // 🔹 Dialogo conferma cancellazione posizione auto
        if (showConfirmDialogAuto) {
            AlertDialog(
                onDismissRequest = { showConfirmDialogAuto = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            clearSavedLocationsAuto(context)
                            showConfirmDialogAuto = false
                            showConfirmMessageAuto = true // ✅ mostra messaggio finale
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
                    Text(
                        "Vuoi davvero cancellare la posizione salvata? Questa azione non può essere annullata."
                    )
                }
            )
        }

        // 🔹 Dialogo messaggio finale dopo cancellazione posizione auto
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
                    //setCenter(latLong)
                    //setZoomLevel(15.toByte())
                }
            }
        }


        // Visualizzazione tabella punti salvati
        FloatingActionButton(
            onClick = { showTable = true },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 30.dp, vertical = 325.dp)
                .zIndex(2f)
        ) {
            Icon(Icons.Filled.Search, contentDescription = "Search saved points")
        }
        if (showTable) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.White)
                    .padding(16.dp)
                    .align(Alignment.Center)
                    .zIndex(999f) // ✅ sopra tutto
            ) {
                LocationsTableScreen(
                    context = context,
                    onBack = { showTable = false } // Azione per chiudere la tabella
                )
            }
        }


        // Pulsante per visualizzare/nascondere punto auto salvato
        FloatingActionButton(
            onClick = {
                mapViewRef?.let { map ->
                    if (!showCarMarker) {
                        // ✅ Mostra punto auto
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

                                    // centra e zoomma
                                    map.model.mapViewPosition.setCenter(geoPoint)
                                    //map.model.mapViewPosition.setZoomLevel(17.toByte())

                                    showCarMarker = true
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "❌ Errore lettura punto auto", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, "⚠️ Nessun punto auto salvato.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        // ❌ Nascondi marker auto
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
                .padding(horizontal = 30.dp, vertical = 325.dp)
                .zIndex(2f)
        ) {Text("Mostra\ninizio")
            /*Icon(
                imageVector = Icons.Default.DirectionsCar,
                contentDescription = if (showCarMarker) "Nascondi punto auto" else "Mostra punto auto"
            )*/
        }



        // Pulsante chiusura app
        var showExitDialog by remember { mutableStateOf(false) }
        val context = LocalContext.current

        FloatingActionButton(
                onClick = { showExitDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(30.dp)
                    .zIndex(2f)
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


