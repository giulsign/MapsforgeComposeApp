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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import it.fourSTL.PositionMarker.ui.theme.MyCustomFont
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.Dialog
import it.fourSTL.PositionMarker.ui.theme.Purple40
import android.util.Log
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.delay
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.Switch
import kotlin.math.cos
import kotlin.math.pow
import org.mapsforge.core.util.MercatorProjection
import androidx.compose.material3.Divider
import androidx.compose.material3.TextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.Switch
import kotlin.math.abs
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.OutlinedButton


//  memorize selected metadata
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


// Copy file italy.map from assets
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


// ID sequential
fun getNextId(context: Context): Int {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val nextId = prefs.getInt("last_id", 0) + 1
    prefs.edit { putInt("last_id", nextId) }
    return nextId
}


// Delete JSON data table
fun clearSavedLocations(context: Context) {
    val file = File(context.filesDir, "locations.json")
    if (file.exists()) {
        file.writeText("[]")
    }
    Toast.makeText(context, "Dati tabella JSON cancellati", Toast.LENGTH_LONG).show()
}


// Delete start position
fun clearSavedLocationsAuto(context: Context) {
    val file = File(context.filesDir, "auto_locations.json")
    if (file.exists()) {
        file.writeText("[]")
    }
    Toast.makeText(context, "Start point deleted", Toast.LENGTH_LONG).show()
}


// Save location to JSON
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

    Toast.makeText(context, "New point added to JSON table", Toast.LENGTH_LONG).show()
}


// Import gps metadatas external point form file
fun importMetadataFromFile(
    context: Context,
    uri: Uri,
    onSuccess: (Int) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        if (inputStream == null) {
            onError("Cannot open file")
            return
        }

        val content = inputStream.bufferedReader().use { it.readText() }

        try {
            val importedArray = JSONArray(content)
            if (importedArray.length() == 0) {
                onError("File is empty")
                return
            }

            // Read the existing JSON file
            val existingFile = File(context.filesDir, "locations.json")
            val existingArray: JSONArray = if (existingFile.exists() && existingFile.length() > 0) {
                JSONArray(existingFile.readText())
            } else {
                JSONArray()
            }

            // Find last used ID
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            var lastId = prefs.getInt("last_id", 0)

            // Add points with new ID
            var importedCount = 0
            for (i in 0 until importedArray.length()) {
                val importedPoint = importedArray.getJSONObject(i)

                lastId++

                val newPoint = JSONObject().apply {
                    put("id", lastId)
                    put("latitude", importedPoint.getDouble("latitude"))
                    put("longitude", importedPoint.getDouble("longitude"))
                    put("altitude", importedPoint.optDouble("altitude", 0.0))
                    put("date", importedPoint.optString("date", ""))
                    put("hour", importedPoint.optString("hour", ""))

                    // Copia tutti gli altri metadati
                    val keys = importedPoint.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (key !in listOf("id", "latitude", "longitude", "altitude", "date", "hour")) {
                            put(key, importedPoint.get(key))
                        }
                    }
                }

                existingArray.put(newPoint)
                importedCount++
            }

            // Save array
            existingFile.writeText(existingArray.toString(2))

            // Upload last ID
            prefs.edit().putInt("last_id", lastId).apply()

            onSuccess(importedCount)

        } catch (e: Exception) {
            onError("Invalid JSON format: ${e.message}")
        }

    } catch (e: Exception) {
        onError("Error reading file: ${e.message}")
    }
}


// Save start point (once - delete to save new one)
fun saveLocationToJsonAuto(context: Context, location: Location) {
    val file = File(context.filesDir, "auto_locations.json")

    if (file.exists() && file.length() > 0) {
        try {
            val jsonArray = JSONArray(file.readText())
            if (jsonArray.length() > 0) {
                Toast.makeText(
                    context,
                    "⚠️ Start point already saved.",
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

    Toast.makeText(context, "✅ New start point saved.", Toast.LENGTH_LONG).show()
}


// Export Start Point in Download
fun exportStartPointToDownload(context: Context) {
    try {
        val sourceFile = File(context.filesDir, "auto_locations.json")

        if (!sourceFile.exists()) {
            Toast.makeText(context, "No Start Point file found", Toast.LENGTH_SHORT).show()
            return
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val fileName = "fourSTLPositionMarker_Start_Point_$timestamp.json"

        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadDir.exists()) downloadDir.mkdirs()

        val destFile = File(downloadDir, fileName)

        FileInputStream(sourceFile).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }

        Toast.makeText(context, "File saved in Download folder as $fileName", Toast.LENGTH_LONG).show()

    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error exporting Start Point position file: ${e.message}", Toast.LENGTH_LONG).show()
    }
}


// Import start point from file
fun importStartPointFromFile(
    context: Context,
    uri: Uri,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        if (inputStream == null) {
            onError("Cannot open file")
            return
        }

        val content = inputStream.bufferedReader().use { it.readText() }

        // Check json
        try {
            val jsonArray = JSONArray(content)
            if (jsonArray.length() == 0) {
                onError("File is empty")
                return
            }

            // Check Objects
            val firstObj = jsonArray.getJSONObject(0)
            if (!firstObj.has("latitude") || !firstObj.has("longitude")) {
                onError("Invalid file format: missing coordinates")
                return
            }

            // Save in auto_locations.json
            val destFile = File(context.filesDir, "auto_locations.json")
            destFile.writeText(content)

            onSuccess()

        } catch (e: Exception) {
            onError("Invalid JSON format: ${e.message}")
        }

    } catch (e: Exception) {
        onError("Error reading file: ${e.message}")
    }
}


// Search and check for existing start point
fun hasExistingStartPoint(context: Context): Boolean {
    val file = File(context.filesDir, "auto_locations.json")
    if (!file.exists() || file.length() == 0L) {
        return false
    }

    try {
        val jsonArray = JSONArray(file.readText())
        return jsonArray.length() > 0
    } catch (e: Exception) {
        return false
    }
}


// Export json in Download
fun exportJsonToDownload(context: Context) {
    try {
        val sourceFile = File(context.filesDir, "locations.json")

        if (!sourceFile.exists()) {
            Toast.makeText(context, "No JSON file found", Toast.LENGTH_SHORT).show()
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

        Toast.makeText(context, "File saved in Download folder as $fileName", Toast.LENGTH_LONG).show()

    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error exporting JSON file: ${e.message}", Toast.LENGTH_LONG).show()
    }
}


fun metersToPixels(
    meters: Float,
    zoomLevel: Byte,
    latitude: Double,
    tileSize: Int = 256
): Float {
    // Raggio Terra in metri
    val earthRadius = 6378137.0

    // Circonferenza Terra all'equatore in metri
    val earthCircumference = 2 * Math.PI * earthRadius

    // Metri per pixel all'equatore per questo zoom
    val metersPerPixelAtEquator = earthCircumference / (tileSize * (2.0.pow(zoomLevel.toInt())))

    // Correzione per latitudine (proiezione Mercatore)
    // Ai poli la scala è diversa dall'equatore
    val latitudeRadians = Math.toRadians(latitude)
    val metersPerPixel = metersPerPixelAtEquator * cos(latitudeRadians)

    // Converti metri in pixel
    return (meters / metersPerPixel).toFloat()
}

/**
 * Versione con MapView (usa i parametri della mappa)
 */
fun metersToPixelsFromMap(
    meters: Float,
    mapView: MapView,
    latitude: Double
): Float {
    val zoomLevel = mapView.model.mapViewPosition.zoomLevel
    val tileSize = mapView.model.displayModel.tileSize
    return metersToPixels(meters, zoomLevel, latitude, tileSize)
}


object TrackWidthConstants {
    // Larghezze preset comuni (in metri)
    const val FOREST_CENSUS_MIN = 10.0f
    const val FOREST_CENSUS_MAX = 20.0f
    const val TRAIL_MAPPING = 5.0f
    const val NARROW_PATH = 2.0f

    // Limiti validazione
    const val MIN_WIDTH = 0.1f   // 10 cm
    const val MAX_WIDTH = 1000.0f // 1 km

    // Larghezza default se non specificata
    const val DEFAULT_WIDTH_PIXELS = 12.0f
}


// Composable for vertical buttons
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


// Composable for dropdown menu
@Composable
fun CategoryMenu(
    title: String,
    items: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
    titleAlignment: TextAlign = TextAlign.Center,
    customFont: FontFamily = MyCustomFont
) {
    Dialog(
        onDismissRequest = onDismiss) {
        Surface(shape = RectangleShape,
            color = Color.White, //Color(0xB3E0E0E0),
            //tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border((1.dp), Color(0xFF99CCFF))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontFamily = customFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 25.sp,
                    textAlign = titleAlignment,
                    color = Color.DarkGray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
                items.forEach { (itemText, itemAction) ->
                    TextButton(
                        onClick = {
                            itemAction()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border((1.dp), Color(0xFF99CCFF))
                    ) {
                        Text(
                            text = itemText,
                            fontFamily = customFont,
                            fontSize = 20.sp,
                            color = Color.Blue,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}


// FUNCTION CALCULATE GPX TRACK DATAS FOR REPORT GPX
data class GpxStats(
    val startDate: String,
    val startTime: String,
    val endDate: String,
    val endTime: String,
    val totalDistance: Float
)

fun calculateGpxStats(points: List<LatLong>): GpxStats? {
    if (points.isEmpty()) return null

    var totalDistance = 0f

    // Calculate distance and elevation
    for (i in 0 until points.size - 1) {
        val current = points[i]
        val next = points[i + 1]

        // Distance calculation
        val results = FloatArray(1)
        Location.distanceBetween(
            current.latitude, current.longitude,
            next.latitude, next.longitude,
            results
        )
        totalDistance += results[0]
    }

    val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    return GpxStats(
        startDate = currentDate,
        startTime = currentTime,
        endDate = currentDate,
        endTime = currentTime,
        totalDistance = totalDistance / 1000f
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
    var onShowCategory by remember { mutableStateOf(false) }
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
    var showStartPointReport by remember { mutableStateOf(false) }

    // 🔹 Load persistent metadatas selections
    val persistentSelectionsState = remember {
        mutableStateOf(loadPersistentSelectionsSet(context))
    }
    var persistentMetadata by remember { mutableStateOf(loadPersistentMetadata(context)) }
    var filteredMarkers by remember { mutableStateOf<List<Marker>>(emptyList()) }

    var savedLocations by remember { mutableStateOf(listOf<String>()) }
    val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    //  Operations on metadatas - imports - create new personal metadatas category
    var pendingImportMetadataUri by remember { mutableStateOf<Uri?>(null) }

    // 🔹 real time tracking service state run
    val realTimeTrackPoints by GpsTrackingService.trackPointsFlow.collectAsState()
    var realTimePolyline by remember { mutableStateOf<Polyline?>(null) }
    var isTracking by remember { mutableStateOf(false) }
    var fileName by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }

    // 🔹 Collect real time tracking points
    val trackPoints by GpsTrackingService.trackPointsFlow.collectAsState()

    // 🔹 Show GPX track
    var loadedGpxTrack by remember { mutableStateOf<List<LatLong>>(emptyList()) }
    var loadedGpxPolyline by remember { mutableStateOf<Polyline?>(null) }
    var showLoadedGpxTrack by remember { mutableStateOf(true) }
    var isTrackingActive by remember { mutableStateOf(false) }
    var loadedGpxFileName by remember { mutableStateOf("") }
    var showGpxReport by remember { mutableStateOf(false) }
    var gpxStats by remember { mutableStateOf<GpxStats?>(null) }
    var showStopTrackingDialog by remember { mutableStateOf(false) }
    var gpxStatsRealTime by remember { mutableStateOf<GpxStats?>(null) }
    var showGpxReportRealTime by remember { mutableStateOf(false) }
    var trackWidthMeters by remember { mutableStateOf<Float?>(null) } // Larghezza traccia corrente
    var showTrackWidthDialog by remember { mutableStateOf(false) }
    var loadedGpxWidth by remember { mutableStateOf<Float?>(null) } // Larghezza GPX caricato


    // 🔹 Show loaded start point from file
    var showImportStartPointDialog by remember { mutableStateOf(false) }
    var showOverwriteConfirmDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }


    // Show buttons menu
    var showPosizioneMenu by remember { mutableStateOf(false) }
    var showPartenzaMenu by remember { mutableStateOf(false) }
    var showDatiMenu by remember { mutableStateOf(false) }
    var showTracciaMenu by remember { mutableStateOf(false) }

    var zoomLevel by remember { mutableStateOf<Byte>(15) }

    // 🆕 Observer zoom
    ObserveMapZoom(
        mapViewRef = mapViewRef,
        onZoomChanged = { newZoom ->
            zoomLevel = newZoom
            Log.d("MapZoom", "Zoom changed to: $newZoom")
        }
    )


    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp


    // Launcher for location permission
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


    // Launcher for metadata file picker
    val metadataFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                importMetadataFromFile(
                    context = context,
                    uri = uri,
                    onSuccess = { count ->
                        Toast.makeText(
                            context,
                            "✅ Successfully imported $count points with updated IDs",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    onError = { error ->
                        Toast.makeText(
                            context,
                            "❌ Error: $error",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }
    )


    // Launcher for GPX file picker
    val gpxFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                // 🆕 Usa nuovo parser che restituisce GpxData
                val gpxData = GpxParser.parse(context, uri)

                // Estrai nome file
                var name = "Track.gpx"
                if (uri.scheme == "content") {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (index >= 0) {
                                name = cursor.getString(index)
                            }
                        }
                    }
                } else {
                    name = uri.lastPathSegment ?: "Track.gpx"
                }
                loadedGpxFileName = name

                if (gpxData.trackPoints.isNotEmpty()) {
                    loadedGpxTrack = gpxData.trackPoints
                    loadedGpxWidth = gpxData.trackWidthMeters // 🆕 Salva larghezza
                    showLoadedGpxTrack = true

                    val widthInfo = gpxData.trackWidthMeters?.let { " (width: ${it}m)" } ?: ""
                    Toast.makeText(
                        context,
                        "✅ GPX loaded: ${gpxData.trackPoints.size} points$widthInfo",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "❌ Error: Cannot load GPX track",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    )


    // Launcher for start point file picker
    val startPointFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                // Check if start point exixsts
                if (hasExistingStartPoint(context)) {
                    pendingImportUri = uri
                    showOverwriteConfirmDialog = true
                } else {
                    // Import
                    importStartPointFromFile(
                        context = context,
                        uri = uri,
                        onSuccess = {
                            Toast.makeText(
                                context,
                                "✅ Start point imported successfully",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        onError = { error ->
                            Toast.makeText(
                                context,
                                "❌ Error: $error",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
        }
    )


    // Check map file
    val mapFile = copyMapFileIfNeeded(context, mapFileName)
    if (!mapFile.exists()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.LightGray)
                .border(25.dp, Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("Map file not found: ${mapFile.absolutePath}")
        }
        return
    }

    // Marker for user location
    var userMarker: Marker? by remember { mutableStateOf(null) }

    fun createRedMarker(context: Context, latLong: LatLong): Marker {
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_marker_red)
        val bitmap = AndroidGraphicFactory.convertToBitmap(drawable)
        return Marker(latLong, bitmap, 0, -bitmap.height / 2)
    }

    // Perpetual location updates
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

                // Perpetual location updates
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


                isTrackingActive = true

                // Timeout GPS: 20 seconds
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

    // Create and show mapview
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(WindowInsets.systemBars.asPaddingValues())
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

                // Start location without permission
                val startLatLong = LatLong(45.4642, 9.19)
                mapView.model.mapViewPosition.setCenter(startLatLong)
                mapView.model.mapViewPosition.setZoomLevel(15.toByte())

                // Create marker for user location when GPS service is not running
                userMarker = createRedMarker(context, startLatLong)
                mapView.layerManager.layers.add(userMarker)

                mapViewRef = mapView
                mapView
            },
            update = { mapView ->
                userLocation?.let { loc ->
                    val latLong = LatLong(loc.latitude, loc.longitude)

                    // Set user location on realtime position when GPS service is runnign
                    userMarker?.latLong = latLong

                    if (!isInitialLocationSet) {
                        mapView.model.mapViewPosition.setCenter(latLong)
                        mapView.model.mapViewPosition.setZoomLevel(15.toByte())
                        isInitialLocationSet = true
                    }

                    mapView.invalidate()
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Button MyLocation
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
                contentDescription = "Go to my location",
                tint = Color.Unspecified
            )
        }

        // 🔹 Button save position
        FloatingActionButton(
            onClick = {
                userLocation?.let { loc ->
                    persistentMetadata = loadPersistentMetadata(context)

                    val finalMetadata = persistentMetadata + selectedMetadata
                    saveLocationToJson(context, loc, finalMetadata)

                    // Logic to save metadata
                    if (persistentMetadata.isNotEmpty()) {
                        selectedMetadata = mapOf("Number" to "1")

                        Toast.makeText(context, "Point saved.", Toast.LENGTH_SHORT).show()
                    } else {
                        selectedMetadata = emptyMap()

                        Toast.makeText(context, "Point saved.", Toast.LENGTH_SHORT).show()
                    }
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
        )
        {
            Icon(
                painter = painterResource(id = R.drawable.save),
                contentDescription = "Save",
                tint = Color.Unspecified
            )
        }

        // Button save start point
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
                contentDescription = "Save start point",
                tint = Color.Unspecified
            )
        }


        // Dialog: Delete points
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
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("Cancel")
                    }
                },
                title = { Text("Confirm deletion of points") },
                text = {
                    Text("Do you really want to delete the saved points? This action cannot be undone.")
                }
            )
        }


        // Alert after points deletion
        if (showConfirmMessage) {
            AlertDialog(
                onDismissRequest = { showConfirmMessage = false },
                title = { Text("Data deletion completed") },
                text = { Text("The saved points table has been cleared.") },
                confirmButton = {
                    TextButton(onClick = { showConfirmMessage = false }) {
                        Text("OK")
                    }
                }
            )
        }


        // Dialog: confirmo start point deletion
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
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialogAuto = false }) {
                        Text("Cancel")
                    }
                },
                title = { Text("Confirm start point deletion") },
                text = {
                    Text("Do you really want to delete the saved start point? This action cannot be undone.")
                }
            )
        }

        // Final alert after start point deletion
        if (showConfirmMessageAuto) {
            AlertDialog(
                onDismissRequest = { showConfirmMessageAuto = false },
                title = { Text("Start point deletion completed") },
                text = { Text("The start point has been cleared.") },
                confirmButton = {
                    TextButton(onClick = { showConfirmMessageAuto = false }) {
                        Text("OK")
                    }
                }
            )
        }

        // Buttons Zoom
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


        // ========== METADAGTAS MENUS (LEFT SIDE) ==========
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
                        showTable = true//onShowCategory = true
                    },
                    "Reset metadatas selecions" to {
                        clearAllPersistentData(context)
                        persistentSelectionsState.value = mutableSetOf()
                        persistentMetadata = emptyMap()
                        Toast.makeText(
                            context,
                            "None persistent metadatas selctions",
                            Toast.LENGTH_SHORT
                        ).show()
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


        // ========== GPS TRACKS (LEFT SIDE) ==========
        if (buttonsVisible) {
            VerticalCategoryButton(
                text = "GPS TRACK MENU",
                alignment = Alignment.TopEnd,
                onClick = { showTracciaMenu = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(vertical = (144.dp + 70.dp + 2.dp))
                    .zIndex(2f)
            )
        }

        if (showTracciaMenu) {
            val trackingItems = mutableListOf<Pair<String, () -> Unit>>()

            trackingItems.add(
                (if (isTracking) "Stop GPS Tracking" else "Start GPS Tracking") to {
                    if (isTracking) {
                        showStopTrackingDialog = true
                    } else {
                        // 🆕 Mostra dialog per impostare larghezza
                        showTrackWidthDialog = true
                    }
                }
            )

            // Save track (only when recording)
            if (isTracking) {
                trackingItems.add(
                    "Save GPX Track" to {
                        val timestamp = SimpleDateFormat(
                            "yyyyMMdd_HHmmss",
                            Locale.getDefault()
                        ).format(Date())
                        fileName = "track_$timestamp"
                        showSaveDialog = true
                    }
                )

                // Report track on recording
                trackingItems.add(
                    "Recording Track Report" to {
                        if (realTimeTrackPoints.isNotEmpty()) {
                            gpxStatsRealTime = calculateGpxStats(
                                realTimeTrackPoints.map { loc ->
                                    LatLong(loc.latitude, loc.longitude)
                                }
                            )
                            showGpxReportRealTime = true
                        } else {
                            Toast.makeText(
                                context,
                                "No tracking data available yet",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }

            // Load track
            trackingItems.add(
                "Load GPX Track" to { gpxFilePickerLauncher.launch("*/*") }
            )

            // Show GPX Report (for loaded track)
            if (loadedGpxTrack.isNotEmpty()) {
                trackingItems.add(
                    "GPX Track Report" to {
                        gpxStats = calculateGpxStats(loadedGpxTrack)
                        showGpxReport = true
                    }
                )
            }

            // Show/Hide track (if loaded)
            if (loadedGpxTrack.isNotEmpty()) {
                trackingItems.add(
                    (if (showLoadedGpxTrack) "Hide GPX track uploaded" else "Show GPX Track Uploaded") to {
                        showLoadedGpxTrack = !showLoadedGpxTrack
                    }
                )

                // Clear Loaded GPX
                trackingItems.add(
                    "Clear Loaded GPX" to {
                        loadedGpxTrack = emptyList()
                        showLoadedGpxTrack = false
                        loadedGpxFileName = ""
                        Toast.makeText(context, "Track cleared from map", Toast.LENGTH_SHORT)
                            .show()
                    }
                )
            }

            CategoryMenu(
                title = "Gps track Menu",
                items = trackingItems,
                onDismiss = { showTracciaMenu = false }
            )
        }


        if (showTrackWidthDialog) {
            TrackWidthDialog(
                onStart = { widthMeters ->
                    trackWidthMeters = widthMeters
                    isTracking = true

                    // Avvia servizio con larghezza
                    val intent = Intent(context, GpsTrackingService::class.java).apply {
                        action = GpsTrackingService.ACTION_START
                        widthMeters?.let {
                            putExtra("track_width_meters", it)
                        }
                    }
                    context.startService(intent)

                    showTrackWidthDialog = false

                    val widthInfo = widthMeters?.let { "${it}m width" } ?: "standard"
                    Toast.makeText(
                        context,
                        "🎬 Recording started ($widthInfo)",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onDismiss = { showTrackWidthDialog = false }
            )
        }


        // ========== START POINT (RIGHT SIDE) ==========
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
            // Check if start point saved
            val hasStartPoint = hasExistingStartPoint(context)

            // Build the item list
            val startPointItems = mutableListOf<Pair<String, () -> Unit>>()

            // Commands always visibles
            startPointItems.add(
                "Save start point" to {
                    userLocation?.let { loc ->
                        saveLocationToJsonAuto(context, loc)
                    }
                }
            )

            startPointItems.add(
                "Import start point from file" to {
                    startPointFilePickerLauncher.launch("application/json")
                }
            )

            startPointItems.add(
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
                                            val distance =
                                                currentLoc.distanceTo(startLocation) / 1000f
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
                                        "❌ Start point reading error",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "⚠️ No start point saved.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            carMarker?.let { marker ->
                                map.layerManager.layers.remove(marker)
                            }
                            carMarker = null
                            showCarMarker = false
                            Toast.makeText(context, "Start point removed.", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            )

            // Commands visible only if start point exist
            if (hasStartPoint) {
                startPointItems.add(
                    "Start point report" to {
                        val file = File(context.filesDir, "auto_locations.json")
                        if (file.exists() && file.length() > 0) {
                            try {
                                val jsonArray = JSONArray(file.readText())
                                if (jsonArray.length() > 0) {
                                    showStartPointReport = true
                                }
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "❌ Error reading start point.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                )

                startPointItems.add(
                    "Export start point" to {
                        exportStartPointToDownload(context)
                    }
                )

                startPointItems.add(
                    "Remove start point" to {
                        showConfirmDialogAuto = true
                    }
                )
            }

            CategoryMenu(
                title = "Start point menu",
                items = startPointItems,
                onDismiss = { showPartenzaMenu = false }
            )
        }


        // ========== METADATAS IMPORT AND OPERATIONS ON PERSONAL METADATAS TABLES (RIGHT SIDE) ==========
        if (buttonsVisible) {
            VerticalCategoryButton(
                text = "PERSONALIZED METADATAS",
                alignment = Alignment.TopStart,
                onClick = { showDatiMenu = true },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(vertical = (144.dp + 70.dp + 2.dp))
                    .zIndex(2f)
            )
        }

        if (showDatiMenu) {
            // Build the item list - always visible
            val metadataItems = mutableListOf<Pair<String, () -> Unit>>()

            // Import metadatas
            metadataItems.add(
                "Import metadatas from file" to {
                    metadataFilePickerLauncher.launch("application/json")
                }
            )

            /*metadataItems.add(
                "Export metadatas to file" to {
                    exportJsonToDownload(context)
                }
            )

            metadataItems.add(
                 "View saved metadatas" to {
                    showTable = true
                }
            )*/

            CategoryMenu(
                title = "Personalized Metadatas Menu",
                items = metadataItems,
                onDismiss = { showDatiMenu = false }
            )
        }


        // Marker GPS uploaded
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


        // 🔹 Draw real time track in blue color
        LaunchedEffect(realTimeTrackPoints, mapViewRef, trackWidthMeters, zoomLevel) {
            val mapView = mapViewRef ?: return@LaunchedEffect
            realTimePolyline?.let { mapView.layerManager.layers.remove(it) }

            if (realTimeTrackPoints.size > 1) {
                val firstPoint = realTimeTrackPoints.first()
                val strokeWidth = trackWidthMeters?.let { meters ->
                    metersToPixelsFromMap(meters, mapView, firstPoint.latitude)
                } ?: TrackWidthConstants.DEFAULT_WIDTH_PIXELS

                Log.d("TrackWidth", "Real-time: ${trackWidthMeters}m = ${strokeWidth}px at zoom $zoomLevel")

                val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
                    setStyle(Style.STROKE)
                    color = android.graphics.Color.argb(64, 0, 0, 255)
                    this.strokeWidth = strokeWidth
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


        // 🔹 Draw loaded GPS track in purple color
        LaunchedEffect(loadedGpxTrack, showLoadedGpxTrack, mapViewRef, loadedGpxWidth, zoomLevel) {
            val mapView = mapViewRef ?: return@LaunchedEffect
            loadedGpxPolyline?.let { mapView.layerManager.layers.remove(it) }

            if (showLoadedGpxTrack && loadedGpxTrack.isNotEmpty()) {
                val firstPoint = loadedGpxTrack.first()
                val strokeWidth = loadedGpxWidth?.let { meters ->
                    metersToPixelsFromMap(meters, mapView, firstPoint.latitude)
                } ?: 10f

                Log.d("TrackWidth", "Loaded GPX: ${loadedGpxWidth}m = ${strokeWidth}px at zoom $zoomLevel")

                val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
                    setStyle(Style.STROKE)
                    color = android.graphics.Color.argb(64, 255, 0, 255)
                    this.strokeWidth = strokeWidth
                    setStrokeJoin(Join.ROUND)
                    setStrokeCap(Cap.ROUND)
                }

                val newPolyline = Polyline(paint, AndroidGraphicFactory.INSTANCE).apply {
                    loadedGpxTrack.forEach { point -> addPoint(point) }
                }

                val layerIndex = if (mapView.layerManager.layers.size() > 0) {
                    mapView.layerManager.layers.size() - 1
                } else {
                    0
                }
                mapView.layerManager.layers.add(layerIndex, newPolyline)
                loadedGpxPolyline = newPolyline
            }
        }


        // Icon selection logic for multiple points visualization
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
                        .background(
                            Color.White,
                            shape = MaterialTheme.shapes.large
                        )
                        .zIndex(999f)
                        .clickable(enabled = false) { }
                ) {
                    LocationsTableScreen(
                        context = context,
                        onBack = { showTable = false },
                        onPointClick = { locations ->

                            filteredMarkers.forEach {
                                mapViewRef?.layerManager?.layers?.remove(it)
                            }


                            val loadBitmap: (Int) -> org.mapsforge.core.graphics.Bitmap? =
                                { resId ->
                                    val drawable = ResourcesCompat.getDrawable(
                                        context.resources,
                                        resId,
                                        null
                                    )
                                    drawable?.let {
                                        AndroidGraphicFactory.convertToBitmap(it)
                                    }
                                }

                            // Icons
                            val iconMammals = loadBitmap(R.drawable.ic_mammals)
                            val iconBird = loadBitmap(R.drawable.ic_birds)
                            val iconReptilians = loadBitmap(R.drawable.ic_reptilians)
                            val iconFrog = loadBitmap(R.drawable.ic_frog)
                            val iconBugs = loadBitmap(R.drawable.ic_bugs)
                            val iconSpider = loadBitmap(R.drawable.ic_spider)
                            val iconCrostac = loadBitmap(R.drawable.ic_cancer)
                            val iconGaster = loadBitmap(R.drawable.ic_gaster)
                            val iconBivalv = loadBitmap(R.drawable.ic_bivalv)
                            val iconWorm = loadBitmap(R.drawable.ic_worm)
                            val iconEchin = loadBitmap(R.drawable.ic_echin)
                            val iconCnidar = loadBitmap(R.drawable.ic_cnidar)
                            val iconPorifer = loadBitmap(R.drawable.ic_porifer)
                            val iconFish = loadBitmap(R.drawable.ic_fish)
                            val iconPers = loadBitmap(R.drawable.ic_marker_pers)
                            val iconTruffle = loadBitmap(R.drawable.ic_truffle)
                            val iconMushroom = loadBitmap(R.drawable.ic_mushroom)
                            val iconVenomous = loadBitmap(R.drawable.ic_mushroom_venomous)
                            val iconBush = loadBitmap(R.drawable.ic_bush)
                            val iconFlower = loadBitmap(R.drawable.ic_flower)
                            val iconPlant = loadBitmap(R.drawable.ic_plant)
                            val iconSucculent = loadBitmap(R.drawable.ic_succulent)
                            val iconGarbage = loadBitmap(R.drawable.ic_garbage)
                            val iconFossil = loadBitmap(R.drawable.ic_fossil)
                            val iconDef = loadBitmap(R.drawable.ic_marker_blue)

                            val newMarkers = locations.mapNotNull { loc ->
                                val idsp = loc.idsp ?: ""
                                val prefix = if (idsp.length >= 4) idsp.substring(0, 4)
                                    .uppercase() else "DEFAULT"


                                val selectedBitmap = when (prefix) {
                                    "FAMA" -> iconMammals
                                    "FAUC" -> iconBird
                                    "FARE" -> iconReptilians
                                    "FAAN" -> iconFrog
                                    "FAIN" -> iconBugs
                                    "FAAR" -> iconSpider
                                    "FACR" -> iconCrostac
                                    "FAGA" -> iconGaster
                                    "FABV" -> iconBivalv
                                    "FAAE" -> iconWorm
                                    "FAEC" -> iconEchin
                                    "FACN" -> iconCnidar
                                    "FAPO" -> iconPorifer
                                    "FAPE" -> iconFish
                                    "PERS" -> iconPers
                                    "TART" -> iconTruffle
                                    "FUED" -> iconMushroom
                                    "FUVE" -> iconVenomous
                                    "FLAL" -> iconPlant
                                    "FLAR" -> iconBush
                                    "FLER" -> iconFlower
                                    "FLSU" -> iconSucculent
                                    "GARB" -> iconGarbage
                                    "FOSS" -> iconFossil
                                    else -> iconDef
                                }


                                if (selectedBitmap != null) {
                                    val latLong = LatLong(loc.latitude, loc.longitude)
                                    Marker(
                                        latLong,
                                        selectedBitmap,
                                        0,
                                        -selectedBitmap.height / 2
                                    )
                                } else {
                                    null
                                }
                            }


                            newMarkers.forEach { marker ->
                                mapViewRef?.layerManager?.layers?.add(marker)
                            }


                            filteredMarkers = newMarkers

                            if (locations.isNotEmpty()) {
                                val firstLocation = locations.first()
                                val latLong = LatLong(
                                    firstLocation.latitude,
                                    firstLocation.longitude
                                )
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


        // Icon selection logic for multiple points visualization
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
                        .background(
                            Color.White,
                            shape = MaterialTheme.shapes.large
                        )
                        .zIndex(999f)
                        .clickable(enabled = false) { }
                ) {
                    LocationsTableScreen(
                        context = context,
                        onBack = { showTable = false },
                        onPointClick = { locations ->
                            // Remove old markers
                            filteredMarkers.forEach {
                                mapViewRef?.layerManager?.layers?.remove(it)
                            }

                            // Helper function to upload bitmap
                            val loadBitmap: (Int) -> org.mapsforge.core.graphics.Bitmap? =
                                { resId ->
                                    val drawable = ResourcesCompat.getDrawable(
                                        context.resources,
                                        resId,
                                        null
                                    )
                                    drawable?.let {
                                        AndroidGraphicFactory.convertToBitmap(it)
                                    }
                                }

                            // Check if are single or mulitple points
                            val isSinglePoint = locations.size == 1

                            if (isSinglePoint) {
                                // ===== SINGLE POINT - NORMAL ICONS) =====
                                val iconMammals = loadBitmap(R.drawable.ic_mammals)
                                val iconBird = loadBitmap(R.drawable.ic_birds)
                                val iconReptilians = loadBitmap(R.drawable.ic_reptilians)
                                val iconFrog = loadBitmap(R.drawable.ic_frog)
                                val iconBugs = loadBitmap(R.drawable.ic_bugs)
                                val iconSpider = loadBitmap(R.drawable.ic_spider)
                                val iconCrostac = loadBitmap(R.drawable.ic_cancer)
                                val iconGaster = loadBitmap(R.drawable.ic_gaster)
                                val iconBivalv = loadBitmap(R.drawable.ic_bivalv)
                                val iconWorm = loadBitmap(R.drawable.ic_worm)
                                val iconEchin = loadBitmap(R.drawable.ic_echin)
                                val iconCnidar = loadBitmap(R.drawable.ic_cnidar)
                                val iconPorifer = loadBitmap(R.drawable.ic_porifer)
                                val iconFish = loadBitmap(R.drawable.ic_fish)
                                val iconPers = loadBitmap(R.drawable.ic_marker_pers)
                                val iconTruffle = loadBitmap(R.drawable.ic_truffle)
                                val iconMushroom = loadBitmap(R.drawable.ic_mushroom)
                                val iconVenomous = loadBitmap(R.drawable.ic_mushroom_venomous)
                                val iconBush = loadBitmap(R.drawable.ic_bush)
                                val iconFlower = loadBitmap(R.drawable.ic_flower)
                                val iconPlant = loadBitmap(R.drawable.ic_plant)
                                val iconSucculent = loadBitmap(R.drawable.ic_succulent)
                                val iconGarbage = loadBitmap(R.drawable.ic_garbage)
                                val iconFossil = loadBitmap(R.drawable.ic_fossil)
                                val iconDef = loadBitmap(R.drawable.ic_marker_blue)

                                val newMarkers = locations.mapNotNull { loc ->
                                    val idsp = loc.idsp ?: ""
                                    val prefix = if (idsp.length >= 4) idsp.substring(0, 4)
                                        .uppercase() else "DEFAULT"

                                    val selectedBitmap = when (prefix) {
                                        "FAMA" -> iconMammals
                                        "FAUC" -> iconBird
                                        "FARE" -> iconReptilians
                                        "FAAN" -> iconFrog
                                        "FAIN" -> iconBugs
                                        "FAAR" -> iconSpider
                                        "FACR" -> iconCrostac
                                        "FAGA" -> iconGaster
                                        "FABV" -> iconBivalv
                                        "FAAE" -> iconWorm
                                        "FAEC" -> iconEchin
                                        "FACN" -> iconCnidar
                                        "FAPO" -> iconPorifer
                                        "FAPE" -> iconFish
                                        "PERS" -> iconPers
                                        "TART" -> iconTruffle
                                        "FUED" -> iconMushroom
                                        "FUVE" -> iconVenomous
                                        "FLAL" -> iconPlant
                                        "FLAR" -> iconBush
                                        "FLER" -> iconFlower
                                        "FLSU" -> iconSucculent
                                        "GARB" -> iconGarbage
                                        "FOSS" -> iconFossil
                                        else -> iconDef
                                    }

                                    if (selectedBitmap != null) {
                                        val latLong = LatLong(loc.latitude, loc.longitude)
                                        Marker(
                                            latLong,
                                            selectedBitmap,
                                            0,
                                            -selectedBitmap.height / 2
                                        )
                                    } else null
                                }

                                newMarkers.forEach { marker ->
                                    mapViewRef?.layerManager?.layers?.add(marker)
                                }
                                filteredMarkers = newMarkers

                            } else {
                                // ===== MULTIPLE POINTS - MIN ICONS =====
                                val iconMammalsMin = loadBitmap(R.drawable.ic_mammals_min)
                                val iconBirdMin = loadBitmap(R.drawable.ic_birds_min)
                                val iconReptiliansMin = loadBitmap(R.drawable.ic_reptilians_min)
                                val iconFrogMin = loadBitmap(R.drawable.ic_frog_min)
                                val iconBugsMin = loadBitmap(R.drawable.ic_bugs_min)
                                val iconSpiderMin = loadBitmap(R.drawable.ic_spider_min)
                                val iconCrostacMin = loadBitmap(R.drawable.ic_cancer_min)
                                val iconGasterMin = loadBitmap(R.drawable.ic_gaster_min)
                                val iconBivalvMin = loadBitmap(R.drawable.ic_bivalv_min)
                                val iconWormMin = loadBitmap(R.drawable.ic_worm_min)
                                val iconEchinMin = loadBitmap(R.drawable.ic_echin_min)
                                val iconCnidarMin = loadBitmap(R.drawable.ic_cnidar_min)
                                val iconPoriferMin = loadBitmap(R.drawable.ic_porifer_min)
                                val iconFishMin = loadBitmap(R.drawable.ic_fish_min)
                                val iconPersMin = loadBitmap(R.drawable.ic_marker_pers_min)
                                val iconTruffleMin = loadBitmap(R.drawable.ic_truffle_min)
                                val iconMushroomMin = loadBitmap(R.drawable.ic_mushroom_min)
                                val iconVenomousMin =
                                    loadBitmap(R.drawable.ic_mushroom_venomous_min)
                                val iconBushMin = loadBitmap(R.drawable.ic_bush_min)
                                val iconFlowerMin = loadBitmap(R.drawable.ic_flower_min)
                                val iconPlantMin = loadBitmap(R.drawable.ic_plant_min)
                                val iconSucculentMin = loadBitmap(R.drawable.ic_succulent_min)
                                val iconGarbageMin = loadBitmap(R.drawable.ic_garbage_min)
                                val iconFossilMin = loadBitmap(R.drawable.ic_fossil_min)
                                val iconDef = loadBitmap(R.drawable.ic_marker_blue)

                                val newMarkersMin = locations.mapNotNull { loc ->
                                    val idsp = loc.idsp ?: ""
                                    val prefix = if (idsp.length >= 4) idsp.substring(0, 4)
                                        .uppercase() else "DEFAULT"

                                    val selectedBitmap = when (prefix) {
                                        "FAMA" -> iconMammalsMin
                                        "FAUC" -> iconBirdMin
                                        "FARE" -> iconReptiliansMin
                                        "FAAN" -> iconFrogMin
                                        "FAIN" -> iconBugsMin
                                        "FAAR" -> iconSpiderMin
                                        "FACR" -> iconCrostacMin
                                        "FAGA" -> iconGasterMin
                                        "FABV" -> iconBivalvMin
                                        "FAAE" -> iconWormMin
                                        "FAEC" -> iconEchinMin
                                        "FACN" -> iconCnidarMin
                                        "FAPO" -> iconPoriferMin
                                        "FAPE" -> iconFishMin
                                        "PERS" -> iconPersMin
                                        "TART" -> iconTruffleMin
                                        "FUED" -> iconMushroomMin
                                        "FUVE" -> iconVenomousMin
                                        "FLAL" -> iconPlantMin
                                        "FLAR" -> iconBushMin
                                        "FLER" -> iconFlowerMin
                                        "FLSU" -> iconSucculentMin
                                        "GARB" -> iconGarbageMin
                                        "FOSS" -> iconFossilMin
                                        else -> iconDef
                                    }

                                    if (selectedBitmap != null) {
                                        val latLong = LatLong(loc.latitude, loc.longitude)
                                        Marker(
                                            latLong,
                                            selectedBitmap,
                                            0,
                                            -selectedBitmap.height / 2
                                        )
                                    } else null
                                }

                                newMarkersMin.forEach { marker ->
                                    mapViewRef?.layerManager?.layers?.add(marker)
                                }
                                filteredMarkers = newMarkersMin
                            }

                            userLocation?.let { currentUserLoc ->
                                val radiusInMeters = 5000f
                                val userLatLong =
                                    LatLong(currentUserLoc.latitude, currentUserLoc.longitude)

                                // FILTER POINTS
                                val pointsToShow = locations.mapNotNull { loc ->
                                    val targetLoc = Location("").apply {
                                        latitude = loc.latitude
                                        longitude = loc.longitude
                                    }
                                    if (currentUserLoc.distanceTo(targetLoc) <= radiusInMeters) {
                                        LatLong(loc.latitude, loc.longitude)
                                    } else {
                                        null
                                    }
                                }.toMutableList()

                                // ADD USER POSITION
                                pointsToShow.add(userLatLong)

                                // SET BOUNDING BOX
                                if (pointsToShow.isNotEmpty()) {
                                    var minLat = 180.0
                                    var maxLat = -180.0
                                    var minLon = 180.0
                                    var maxLon = -180.0

                                    for (p in pointsToShow) {
                                        if (p.latitude < minLat) minLat = p.latitude
                                        if (p.latitude > maxLat) maxLat = p.latitude
                                        if (p.longitude < minLon) minLon = p.longitude
                                        if (p.longitude > maxLon) maxLon = p.longitude
                                    }

                                    val boundingBox = org.mapsforge.core.model.BoundingBox(
                                        minLat,
                                        minLon,
                                        maxLat,
                                        maxLon
                                    )

                                    // 4. UPDATE MAP VIEW BOUNDING BOX
                                    mapViewRef?.let { map ->
                                        val dimension = map.model.mapViewDimension.dimension
                                        if (dimension != null) {
                                            val calculatedZoom =
                                                org.mapsforge.core.util.LatLongUtils.zoomForBounds(
                                                    dimension,
                                                    boundingBox,
                                                    map.model.displayModel.tileSize
                                                )

                                            val finalZoom =
                                                if (calculatedZoom > 17) 17.toByte() else calculatedZoom

                                            map.model.mapViewPosition.setCenter(boundingBox.centerPoint)
                                            map.model.mapViewPosition.setZoomLevel(finalZoom)
                                        }
                                    }
                                }
                            } ?: run {
                                // FALLBACK: IF NO USER LOCATION
                                if (locations.isNotEmpty()) {
                                    val firstLocation = locations.first()
                                    val latLong =
                                        LatLong(firstLocation.latitude, firstLocation.longitude)
                                    mapViewRef?.model?.mapViewPosition?.apply {
                                        setCenter(latLong)
                                        setZoomLevel(17.toByte())
                                    }
                                }
                            }

                            // CLOSE TABLE
                            showTable = false
                        }
                    )
                }
            }
        }


        // Button Show/Hide start point
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

                                    // Measure distance from start point
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
                                                "📍 Start point visible. Distance: %.2f km",
                                                distance
                                            ),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } ?: run {
                                        Toast.makeText(
                                            context,
                                            "📍 Start point visible.",
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
                            "Start point hided.",
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
        )
        {
            Icon(
                painter = painterResource(id = R.drawable.show_start),
                contentDescription = "Mostra punto partenza",
                tint = Color.Unspecified
            )
        }


        // 🔹 Button open metadatas SelectionScreen
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
        )
        {
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
                Box {
                    SelectionScreen(
                        onDismiss = {
                            showSelectionScreen = false

                            val prefs = context.getSharedPreferences(
                                "metadata_prefs",
                                Context.MODE_PRIVATE
                            )
                            val savedIds = prefs.getStringSet(
                                "persistent_selections_set",
                                emptySet()
                            ) ?: emptySet()

                            val newPersistentList = mutableListOf<String>()
                            savedIds.forEach { id ->

                                val jsonString =
                                    prefs.getString("persistent_item_$id", null)
                                if (jsonString != null) {
                                    try {
                                        val json = JSONObject(jsonString)
                                        val title = json.optString("title")
                                        val note = json.optString("note")

                                        val displayText =
                                            if (title.isNotEmpty()) "$title $note" else note
                                        if (displayText.isNotBlank()) {
                                            newPersistentList.add(displayText)
                                        }
                                    } catch (e: Exception) {
                                        newPersistentList.add(id)
                                    }
                                } else {
                                    newPersistentList.add(id)
                                }
                            }
                            persistentSelectionsState.value = newPersistentList.toMutableSet()
                        },
                        onSave = { metadata, isPersistent ->
                            showSelectionScreen = false

                            val finalMetadata = metadata.toMutableMap()


                            if (!finalMetadata.containsKey("Number") || finalMetadata["Number"].isNullOrBlank()) {
                                finalMetadata["Number"] = "1"
                            }


                            // Temporary metadatas (yellow)
                            selectedMetadata = finalMetadata

                            // Reload temporary and persistent metadatas
                            persistentMetadata = loadPersistentMetadata(context)
                            persistentSelectionsState.value =
                                loadPersistentSelectionsSet(context)

                            Toast.makeText(
                                context,
                                "✅ Temporary: ${metadata.size} | Persistent: ${persistentMetadata.size}",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        persistentSelections = persistentSelectionsState.value
                    )
                }
            }
        }


        // Button toggle visible buttons
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
                    text = "Hide Menus",
                    color = Color.Red,
                    fontSize = 27.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = MyCustomFont,
                    fontWeight = FontWeight.Bold
                )
            else
                Text(
                    text = "Show Menus",
                    color = Color.Green,
                    fontSize = 27.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = MyCustomFont,
                    fontWeight = FontWeight.Bold
                )
        }


        // Button licenses and infos
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


        // licensees page
        if (showLicenses) {
            var tabIndex by remember { mutableStateOf(0) }
            val tabs = listOf("Dependencies", "App License", "Third Party License", "Readme")

            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1001f),
                topBar = {
                    Column {
                        TopAppBar(
                            title = { Text("Licenses and Info") },
                            navigationIcon = {
                                IconButton(onClick = { showLicenses = false }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            }
                        )

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


        // Dialog for track save
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text("Save GPS Track", fontFamily = MyCustomFont) },
                text = {
                    Column {
                        TextField(
                            value = fileName,
                            onValueChange = { fileName = it },
                            label = { Text("File name") },
                            singleLine = true
                        )

                        // 🆕 Mostra larghezza se impostata
                        trackWidthMeters?.let { width ->
                            Spacer(Modifier.height(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFE8F5E9)
                                ),
                                shape = RectangleShape
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Straighten,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Track width: ${width}m",
                                        fontSize = 14.sp,
                                        fontFamily = MyCustomFont,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            // 🆕 Passa larghezza al servizio
                            val intent = Intent(context, GpsTrackingService::class.java).apply {
                                action = GpsTrackingService.ACTION_SAVE
                                putExtra(GpsTrackingService.EXTRA_FILENAME, fileName)
                                trackWidthMeters?.let {
                                    putExtra(GpsTrackingService.EXTRA_TRACK_WIDTH, it)
                                }
                            }
                            context.startService(intent)
                            showSaveDialog = false
                        }
                    ) {
                        Text("Save", fontFamily = MyCustomFont)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text("Cancel", fontFamily = MyCustomFont)
                    }
                }
            )
        }


        // Button close app
        var showExitDialog by remember { mutableStateOf(false) }

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
        }


        // Advisor GPX loaded
        if (loadedGpxTrack.isNotEmpty()) {
            Text(
                text = "Loaded GPX: $loadedGpxFileName",
                color = Color.Blue,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MyCustomFont,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 270.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.3f),
                        shape = RectangleShape
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }


        // GPS tracking advisor
        if (realTimeTrackPoints.isNotEmpty()) {
            Text(
                text = "GPS track recording ON",
                color = Color.Red,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MyCustomFont,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 310.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.3f),
                        shape = RectangleShape
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }


        // Advisor Metadata selected (Temporary or Persistent)
        val activeMetadataText = if (selectedMetadata.isNotEmpty()) {
            if (selectedMetadata.size == 1 && selectedMetadata.containsKey("Number")) {
                val validItems = persistentSelectionsState.value.filter { it.isNotBlank() }
                if (validItems.isNotEmpty()) {
                    "Selection: " + validItems.joinToString(", ") + " (Num: ${selectedMetadata["Number"]})"
                } else {
                    "Selection: " + selectedMetadata["Number"]
                }
            } else {
                "Selection: " + selectedMetadata.entries
                    .filter { it.key != "idsp" && it.key != "Number" }
                    .joinToString(", ") { it.value } +
                        (if (selectedMetadata.containsKey("Number")) " (Num: ${selectedMetadata["Number"]})" else "")
            }
        } else {
            val validItems = persistentSelectionsState.value.filter { it.isNotBlank() }
            if (validItems.isNotEmpty()) {
                "Selection: " + validItems.joinToString(", ")
            } else {
                ""
            }
        }

        if (activeMetadataText.isNotEmpty()) {
            Text(
                text = activeMetadataText,
                color = Color(0xFF006400),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MyCustomFont,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 160.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.5f),
                        shape = RectangleShape
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }


        // Dialog close app
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("Close app") },
                text = { Text("Do you really want to close the app?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showExitDialog = false
                            (context as? Activity)?.finishAffinity()
                        }
                    ) {
                        Text("Ok")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Dialog GPS service down
        if (showGpsDialog) {
            AlertDialog(
                onDismissRequest = { showGpsDialog = false },
                title = { Text("Gps signal not found") },
                text = {
                    Text(
                        "⚠️ No Gps signal found.\n\n" +
                                "Check your device settings and try again."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showGpsDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }

        // Dialog show start point report
        if (showStartPointReport) {
            val file = File(context.filesDir, "auto_locations.json")
            var reportText = "Error loading data"

            if (file.exists() && file.length() > 0) {
                try {
                    val jsonArray = JSONArray(file.readText())
                    if (jsonArray.length() > 0) {
                        val locationJson = jsonArray.getJSONObject(0)
                        val startLat = locationJson.getDouble("latitude")
                        val startLon = locationJson.getDouble("longitude")
                        val startDate = locationJson.getString("date")
                        val startHour = locationJson.getString("hour")

                        val currentDate =
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        val currentHour =
                            SimpleDateFormat("HH:mm:ss.S", Locale.getDefault()).format(Date())

                        val distanceText = userLocation?.let { currentLoc ->
                            val startLocation = Location("").apply {
                                latitude = startLat
                                longitude = startLon
                            }
                            val distance = currentLoc.distanceTo(startLocation) / 1000f
                            String.format(Locale.getDefault(), "%.3f km", distance)
                        } ?: "N/A"

                        val currentLatText = userLocation?.latitude?.let {
                            String.format(Locale.getDefault(), "%.6f", it)
                        } ?: "N/A"

                        val currentLonText = userLocation?.longitude?.let {
                            String.format(Locale.getDefault(), "%.6f", it)
                        } ?: "N/A"

                        reportText = """
                    📍 START POINT REPORT
                    
                    ━━━━━━━━━━━━━━━━━━━━━━
                    START POINT DATA
                    ━━━━━━━━━━━━━━━━━━━━━━
                    📅 Date: $startDate
                    🕐 Time: $startHour
                    🌐 Latitude: ${String.format(Locale.getDefault(), "%.6f", startLat)}
                    🌐 Longitude: ${String.format(Locale.getDefault(), "%.6f", startLon)}
                    
                    ━━━━━━━━━━━━━━━━━━━━━━
                    CURRENT POSITION DATA
                    ━━━━━━━━━━━━━━━━━━━━━━
                    📅 Date: $currentDate
                    🕐 Time: $currentHour
                    🌐 Latitude: $currentLatText
                    🌐 Longitude: $currentLonText
                    
                    ━━━━━━━━━━━━━━━━━━━━━━
                    📏 Distance: $distanceText
                    ━━━━━━━━━━━━━━━━━━━━━━
                """.trimIndent()
                    }
                } catch (e: Exception) {
                    reportText = "❌ Error loading report:\n${e.message}"
                }
            }

            AlertDialog(
                onDismissRequest = { showStartPointReport = false },
                shape = RectangleShape,
                title = {
                    Text(
                        "Start Point Report",
                        fontFamily = MyCustomFont,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    LazyColumn {
                        item {
                            Text(
                                text = reportText,
                                fontFamily = MyCustomFont,
                                fontSize = 16.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showStartPointReport = false }) {
                        Text("OK", fontFamily = MyCustomFont)
                    }
                }
            )
        }

        // Dialog upload start point from file
        if (showOverwriteConfirmDialog) {
            AlertDialog(
                onDismissRequest = {
                    showOverwriteConfirmDialog = false
                    pendingImportUri = null
                },
                shape = RectangleShape,
                title = {
                    Text(
                        "Overwrite existing start point?",
                        fontFamily = MyCustomFont,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        "A start point is already saved. Do you want to overwrite it with the imported file?",
                        fontFamily = MyCustomFont
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingImportUri?.let { uri ->
                                importStartPointFromFile(
                                    context = context,
                                    uri = uri,
                                    onSuccess = {
                                        Toast.makeText(
                                            context,
                                            "✅ Start point overwritten successfully",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    },
                                    onError = { error ->
                                        Toast.makeText(
                                            context,
                                            "❌ Error: $error",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                )
                            }
                            showOverwriteConfirmDialog = false
                            pendingImportUri = null
                        }
                    ) {
                        Text("Overwrite", fontFamily = MyCustomFont)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showOverwriteConfirmDialog = false
                            pendingImportUri = null
                        }
                    ) {
                        Text("Cancel", fontFamily = MyCustomFont)
                    }
                }
            )
        }


        // Dialog show GPX track report
        if (showGpxReport && gpxStats != null) {
            val stats = gpxStats!!

            val reportText = """
        📊 GPX TRACK REPORT
        
        ━━━━━━━━━━━━━━━━━━━━━━
        TRACK FILE
        ━━━━━━━━━━━━━━━━━━━━━━
        📁 File: $loadedGpxFileName
        📍 Points: ${loadedGpxTrack.size}
        
        ━━━━━━━━━━━━━━━━━━━━━━
        START DATA
        ━━━━━━━━━━━━━━━━━━━━━━
        📅 Date: ${stats.startDate}
        🕐 Time: ${stats.startTime}
        🌐 Latitude: ${String.format(Locale.getDefault(), "%.6f", loadedGpxTrack.first().latitude)}
        🌐 Longitude: ${String.format(Locale.getDefault(), "%.6f", loadedGpxTrack.first().longitude)}
        
        ━━━━━━━━━━━━━━━━━━━━━━
        END DATA
        ━━━━━━━━━━━━━━━━━━━━━━
        📅 Date: ${stats.endDate}
        🕐 Time: ${stats.endTime}
        🌐 Latitude: ${String.format(Locale.getDefault(), "%.6f", loadedGpxTrack.last().latitude)}
        🌐 Longitude: ${String.format(Locale.getDefault(), "%.6f", loadedGpxTrack.last().longitude)}
        
        ━━━━━━━━━━━━━━━━━━━━━━
        TRACK STATISTICS
        ━━━━━━━━━━━━━━━━━━━━━━
        📏 Total Distance: ${String.format(Locale.getDefault(), "%.2f km", stats.totalDistance)}
        ━━━━━━━━━━━━━━━━━━━━━━
        """.trimIndent()

            AlertDialog(
                onDismissRequest = { showGpxReport = false },
                shape = RectangleShape,
                title = {
                    Text(
                        "GPX Track Report",
                        fontFamily = MyCustomFont,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    LazyColumn {
                        item {
                            Text(
                                text = reportText,
                                fontFamily = MyCustomFont,
                                fontSize = 16.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showGpxReport = false }) {
                        Text("OK", fontFamily = MyCustomFont)
                    }
                }
            )
        }


        // Dialog show Real-Time GPS track report (recording)
        if (showGpxReportRealTime && gpxStatsRealTime != null) {
            val stats = gpxStatsRealTime!!

            val reportText = """
        📊 REAL-TIME GPS TRACK REPORT
        
        ┌────────────────────────
        RECORDING STATUS
        ┌────────────────────────
        🔴 Status: RECORDING
        📍 Points: ${realTimeTrackPoints.size}
        
        ┌────────────────────────
        START DATA
        ┌────────────────────────
        📅 Date: ${stats.startDate}
        🕐 Time: ${stats.startTime}
        🌍 Latitude: ${
                String.format(
                    Locale.getDefault(),
                    "%.6f",
                    realTimeTrackPoints.firstOrNull()?.latitude ?: 0.0
                )
            }
        🌍 Longitude: ${
                String.format(
                    Locale.getDefault(),
                    "%.6f",
                    realTimeTrackPoints.firstOrNull()?.longitude ?: 0.0
                )
            }
        
        ┌────────────────────────
        CURRENT DATA
        ┌────────────────────────
        📅 Date: ${stats.endDate}
        🕐 Time: ${stats.endTime}
        🌍 Latitude: ${
                String.format(
                    Locale.getDefault(),
                    "%.6f",
                    realTimeTrackPoints.lastOrNull()?.latitude ?: 0.0
                )
            }
        🌍 Longitude: ${
                String.format(
                    Locale.getDefault(),
                    "%.6f",
                    realTimeTrackPoints.lastOrNull()?.longitude ?: 0.0
                )
            }
        
        ┌────────────────────────
        TRACK STATISTICS
        ┌────────────────────────
        📏 Total Distance: ${String.format(Locale.getDefault(), "%.2f km", stats.totalDistance)}
        ┌────────────────────────
        """.trimIndent()

            AlertDialog(
                onDismissRequest = { showGpxReportRealTime = false },
                shape = RectangleShape,
                title = {
                    Text(
                        "Real-Time Track Report",
                        fontFamily = MyCustomFont,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red
                    )
                },
                text = {
                    LazyColumn {
                        item {
                            Text(
                                text = reportText,
                                fontFamily = MyCustomFont,
                                fontSize = 16.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showGpxReportRealTime = false }) {
                        Text("OK", fontFamily = MyCustomFont)
                    }
                }
            )
        }


        // Dialog stop tracking with confirm
        if (showStopTrackingDialog) {
            AlertDialog(
                onDismissRequest = { showStopTrackingDialog = false },
                shape = RectangleShape,
                title = {
                    Text(
                        "Stop GPS Tracking",
                        fontFamily = MyCustomFont,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        "Do you want to save the GPS track before stopping the recording?",
                        fontFamily = MyCustomFont
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            // Prepara il nome file
                            val timestamp = SimpleDateFormat(
                                "yyyyMMdd_HHmmss",
                                Locale.getDefault()
                            ).format(Date())
                            val generatedFileName = "track_$timestamp"

                            // 🆕 Usa la nuova action ACTION_SAVE_AND_STOP
                            val intent = Intent(context, GpsTrackingService::class.java).apply {
                                action = GpsTrackingService.ACTION_SAVE_AND_STOP
                                putExtra(GpsTrackingService.EXTRA_FILENAME, generatedFileName)
                                trackWidthMeters?.let {
                                    putExtra(GpsTrackingService.EXTRA_TRACK_WIDTH, it)
                                }
                            }
                            context.startService(intent)

                            // Aggiorna lo stato locale
                            isTracking = false
                            trackWidthMeters = null
                            showStopTrackingDialog = false
                        }
                    ) {
                        Text("Save and Stop", fontFamily = MyCustomFont)
                    }
                },
                dismissButton = {
                    Column {
                        TextButton(
                            onClick = {
                                // Stop senza salvare
                                val intent = Intent(context, GpsTrackingService::class.java).apply {
                                    action = GpsTrackingService.ACTION_STOP
                                }
                                context.startService(intent)

                                isTracking = false
                                trackWidthMeters = null
                                showStopTrackingDialog = false

                                Toast.makeText(
                                    context,
                                    "Tracking stopped without saving",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Text("Stop without saving", fontFamily = MyCustomFont)
                        }
                        TextButton(
                            onClick = { showStopTrackingDialog = false }
                        ) {
                            Text("Cancel", fontFamily = MyCustomFont)
                        }
                    }
                }
            )
        }
    }
}


@Composable
fun TrackWidthDialog(
    onStart: (widthMeters: Float?) -> Unit,
    onDismiss: () -> Unit
) {
    var widthInput by remember { mutableStateOf("10.0") } // Default 10m
    var useCustomWidth by remember { mutableStateOf(true) }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        title = {
            Text(
                "Start GPS Tracking",
                fontFamily = MyCustomFont,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Set track width for census/survey",
                    fontFamily = MyCustomFont,
                    fontSize = 14.sp
                )

                Divider()

                // Toggle: Usa larghezza custom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Custom track width",
                        fontFamily = MyCustomFont,
                        fontSize = 16.sp
                    )

                    Switch(
                        checked = useCustomWidth,
                        onCheckedChange = { useCustomWidth = it }
                    )
                }

                // Input larghezza (visibile solo se custom è attivo)
                AnimatedVisibility(visible = useCustomWidth) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = widthInput,
                            onValueChange = {
                                widthInput = it
                                showError = false
                            },
                            label = { Text("Width (meters)", fontFamily = MyCustomFont) },
                            placeholder = { Text("e.g., 10.0") },
                            suffix = { Text("m", color = Color.Gray) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            singleLine = true,
                            isError = showError,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (showError) {
                            Text(
                                "⚠️ Invalid value (use 0.1 - 1000)",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontFamily = MyCustomFont
                            )
                        }

                        // Preset buttons
                        Text(
                            "Quick presets:",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            fontFamily = MyCustomFont
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("5", "10", "20", "50").forEach { preset ->
                                OutlinedButton(
                                    onClick = { widthInput = "$preset.0" },
                                    modifier = Modifier.weight(1f),
                                    shape = RectangleShape
                                ) {
                                    Text("${preset}m", fontSize = 12.sp)
                                }
                            }
                        }

                        // Info box
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFF9C4)
                            ),
                            shape = RectangleShape
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "💡 Track Width Info",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    fontFamily = MyCustomFont
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "• Forest census: 10-20m\n" +
                                            "• Trail mapping: 2-5m\n" +
                                            "• Survey strips: custom",
                                    fontSize = 11.sp,
                                    fontFamily = MyCustomFont,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                if (!useCustomWidth) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE3F2FD)
                        ),
                        shape = RectangleShape
                    ) {
                        Text(
                            "ℹ️ Standard track (no custom width)",
                            modifier = Modifier.padding(12.dp),
                            fontSize = 12.sp,
                            fontFamily = MyCustomFont
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (useCustomWidth) {
                        val width = widthInput.toFloatOrNull()
                        if (width != null && width > 0 && width <= 1000) {
                            onStart(width)
                        } else {
                            showError = true
                        }
                    } else {
                        onStart(null) // Nessuna larghezza custom
                    }
                },
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                Spacer(Modifier.width(4.dp))
                Text("Start Recording", fontFamily = MyCustomFont)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = MyCustomFont)
            }
        }
    )
}


// Helper function to load text from assets
@Composable
private fun AssetTextView(assetFileName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val text = remember(assetFileName) {
        try {
            context.assets.open(assetFileName).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "File load error: ${e.message}"
        }
    }

    LazyColumn(modifier = modifier.padding(16.dp)) {
        item {
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}


@Composable
fun ObserveMapZoom(
    mapViewRef: MapView?,
    onZoomChanged: (Byte) -> Unit
) {
    var lastZoom by remember { mutableStateOf<Byte>(15) }

    LaunchedEffect(mapViewRef) {
        mapViewRef?.let { mapView ->
            // Polling ogni 500ms per cambiamenti zoom
            while (true) {
                delay(500)
                val currentZoom = mapView.model.mapViewPosition.zoomLevel
                if (currentZoom != lastZoom) {
                    lastZoom = currentZoom
                    onZoomChanged(currentZoom)
                }
            }
        }
    }
}

@Composable
fun ObserveMapZoomThrottled(
    mapViewRef: MapView?,
    onZoomChanged: (Byte) -> Unit
) {
    var lastZoom by remember { mutableStateOf<Byte>(15) }

    LaunchedEffect(mapViewRef) {
        mapViewRef?.let { mapView ->
            while (true) {
                delay(300) // Check ogni 300ms
                val currentZoom = mapView.model.mapViewPosition.zoomLevel

                // Aggiorna solo se differenza >= 1 livello
                if (kotlin.math.abs(currentZoom - lastZoom) >= 1) {
                    lastZoom = currentZoom
                    onZoomChanged(currentZoom)
                }
            }
        }
    }
}
