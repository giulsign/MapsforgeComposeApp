package com.example.mapsforgecomposeapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
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
import java.io.File

/** copia direttamente italy.map nella cartella di destinazione **/

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
    if (LocalInspectionMode.current) {
        Box(
            modifier = modifier.fillMaxSize().background(Color.Gray),
            contentAlignment = Alignment.Center
        ) {
            Text("Anteprima mappa")
        }
        return
    }

    // Verifica file mappa
    /**val mapFile = File(context.getExternalFilesDir("maps"), mapFileName) **/
    val mapFile = copyMapFileIfNeeded(context, mapFileName) /** indica la posizione del file mappa da copiare **/
    if (!mapFile.exists()) {
        Box(
            modifier = modifier.fillMaxSize().background(Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            Text("File mappa non trovato: ${mapFile.absolutePath}")
        }
        return
    }

    // Crea e mostra MapView
    AndroidView(
        factory = {
            AndroidGraphicFactory.createInstance(context.applicationContext)
            val mapView = MapView(context).apply {
                mapScaleBar.isVisible = true
                setBuiltInZoomControls(true)
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
        modifier = modifier
    )

    // Quando la posizione dell’utente cambia → ricentra + aggiorna marker
    LaunchedEffect(userLocation) {
        userLocation?.let { loc ->
            val latLong = LatLong(loc.latitude, loc.longitude)

            // Se marker non esiste → crealo
            if (userMarker == null && mapViewRef != null) {
                userMarker = createRedMarker(context, latLong)
                mapViewRef?.layerManager?.layers?.add(userMarker)
            } else {
                // aggiorna marker esistente
                userMarker?.latLong = latLong
            }

            // Ricentra la mappa
            mapViewRef?.model?.mapViewPosition?.apply {
                setCenter(latLong)
                setZoomLevel(15.toByte())
            }
        }
    }
}