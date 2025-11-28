package it.fourSTL.PositionMarker

import it.fourSTL.PositionMarker.R
import android.Manifest
import android.R.attr.text
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
import android.graphics.drawable.shapes.Shape
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
import android.os.Looper
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.Dialog
//import androidx.preference.isNotEmpty
import it.fourSTL.PositionMarker.ui.theme.Purple40


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

fun clearPersistentMetadata(context: Context) {
    val prefs = context.getSharedPreferences("metadata_prefs", Context.MODE_PRIVATE)
    prefs.edit().remove("persistent_metadata").apply()
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


// Show saved location on map
fun showSavedLocationOnMapForge(context: Context, map: org.mapsforge.map.android.view.MapView) {
    val file = File(context.filesDir, "auto_locations.json")

    if (!file.exists() || file.length() == 0L) {
        Toast.makeText(context, "⚠️ No start point saved found.", Toast.LENGTH_LONG).show()
        return
    }

    try {
        val jsonArray = JSONArray(file.readText())
        if (jsonArray.length() == 0) {
            Toast.makeText(context, "⚠️ No valid point in file JSON.", Toast.LENGTH_LONG).show()
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

        Toast.makeText(context, "📍 Start point visible on map", Toast.LENGTH_LONG).show()

    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "❌ Error reading start point.", Toast.LENGTH_LONG).show()
    }
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


// Export JSON in Download
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

        // 🔹 Load persistent metadatas selections
        val persistentSelectionsState = remember {
            mutableStateOf(loadPersistentSelectionsSet(context))
        }
        var persistentMetadata by remember { mutableStateOf(loadPersistentMetadata(context)) }
        var filteredMarkers by remember { mutableStateOf<List<Marker>>(emptyList()) }

        var savedLocations by remember { mutableStateOf(listOf<String>()) }
        val fusedLocationClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)

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
        var isTrackingActive by remember {mutableStateOf(false)}
        var loadedGpxFileName by remember { mutableStateOf("") }


        // Show buttons menu
        var showPosizioneMenu by remember { mutableStateOf(false) }
        var showPartenzaMenu by remember { mutableStateOf(false) }
        var showDatiMenu by remember { mutableStateOf(false) }
        var showTracciaMenu by remember { mutableStateOf(false) }


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


        // Launcher for GPX file picker
        val gpxFilePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
            onResult = { uri: Uri? ->
                if (uri != null) {
                    val points = GpxParser.parse(context, uri)

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
                        // Fallback per i rari casi di file://
                        name = uri.lastPathSegment ?: "Track.gpx"
                    }
                    loadedGpxFileName = name

                    if (points.isNotEmpty()) {
                        loadedGpxTrack = points
                        showLoadedGpxTrack = true
                        Toast.makeText(
                            context,
                            "GPX track loaded with ${points.size} points",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Error: Cannot load GPX track from file",
                            Toast.LENGTH_LONG
                        ).show()
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

                        // Reset temporary metadata selection
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


            // ========== LOCATIONS (LEFT SIDE) ==========
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


// ========== GPS TRACKS (LEFT SIDE) ==========
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

                // Start/End recording
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

                // Save track (only when recording)
                if (isTracking) {
                    trackingItems.add(
                        "Save GPX Track" to {
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            fileName = "track_$timestamp"
                            showSaveDialog = true
                        }
                    )
                }

                // Load track
                trackingItems.add(
                    "Load GPX Track" to {gpxFilePickerLauncher.launch("*/*")}
                    )

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
                            Toast.makeText(context, "Track cleared from map", Toast.LENGTH_SHORT).show()
                        }
                    )

                }

                CategoryMenu(
                    title = "Gps track Menu",
                    items = trackingItems,
                    onDismiss = { showTracciaMenu = false }
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


                            // 🔹 Draw loaded GPS track in purple color
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
                            ) {
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

                                                // Temporary metadatas (yellow)
                                                selectedMetadata = metadata

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
                                    title = { Text("Save Track GPX") },
                                    text = {

                                        androidx.compose.material3.TextField(
                                            value = fileName,
                                            onValueChange = { fileName = it },
                                            label = { Text("File name") },
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
                                            Text("Save")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showSaveDialog = false }) {
                                            Text("Cancel")
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
                        .padding(bottom = 240.dp)
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
                        .padding(bottom = 200.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.3f),
                            shape = RectangleShape
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }


            // Advisor Metadata selected (Temporary or Persistent)
            val activeMetadataText = if (selectedMetadata.isNotEmpty()) {
                "Selection: " + selectedMetadata.entries.joinToString(", ") { "${it.value}" } // Mostra solo i valori per brevità
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
        }
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