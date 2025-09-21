/**package com.example.mapsforgecomposeapp

import org.mapsforge.map.android.util.AndroidUtil
import android.content.Context
import android.os.Environment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.renderer.TileRendererLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color


@Composable
fun MapsforgeMapComposable(
    context: Context,
    modifier: Modifier = Modifier
) {
    if (LocalInspectionMode.current) {
        // In preview mostri solo un placeholder
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Gray),
            contentAlignment = Alignment.Center
        ) {
            Text("Anteprima mappa")
        }
    } else {
        // CODICE ORIGINALE → gira solo a runtime
        AndroidView(
            factory = {
                AndroidGraphicFactory.createInstance(context.applicationContext)

                val mapView = MapView(context).apply {
                    mapScaleBar.isVisible = true
                    setBuiltInZoomControls(true)
                    setZoomLevelMin(2.toByte())
                    setZoomLevelMax(20.toByte())
                }

                val mapFile = MapFile(
                    Environment.getExternalStorageDirectory()
                        .resolve("maps/italy.map")
                )

                val tileCache: TileCache = AndroidUtil.createTileCache(
                    context,
                    "mapcache",
                    mapView.model.displayModel.tileSize,
                    1f,
                    mapView.model.frameBufferModel.overdrawFactor
                )

                val renderer = TileRendererLayer(
                    tileCache,
                    mapFile,
                    mapView.model.mapViewPosition,
                    AndroidGraphicFactory.INSTANCE
                ).apply {
                    setXmlRenderTheme(AssetsRenderTheme(context))
                }

                mapView.layerManager.layers.add(renderer)

                mapView.model.mapViewPosition.setCenter(LatLong(45.4642, 9.19))
                mapView.model.mapViewPosition.setZoomLevel(12.toByte())

                mapView
            },
            modifier = modifier
        )
    }
}**/



/**
package com.example.mapsforgecomposeapp

import org.mapsforge.map.android.util.AndroidUtil
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.renderer.TileRendererLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color

@Composable
fun MapsforgeMapComposable(
    context: Context,
    modifier: Modifier = Modifier
) {
    var permissionGranted by remember { mutableStateOf(false) }

    // Launcher per chiedere permessi runtime
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
    }

    // Controllo permessi all'avvio
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT < 33) {
            // Android 12 e inferiori
            permissionGranted = PackageManager.PERMISSION_GRANTED ==
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context, Manifest.permission.READ_EXTERNAL_STORAGE
                    )
            if (!permissionGranted) {
                launcher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        } else {
            // Android 13+: normalmente si usa Scoped Storage, ma per file generici chiediamo permesso legacy
            permissionGranted = true
        }
    }

    if (LocalInspectionMode.current) {
        // Preview
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Gray),
            contentAlignment = Alignment.Center
        ) {
            Text("Anteprima mappa")
        }
    } else if (permissionGranted) {
        // Runtime
        AndroidView(
            factory = {
                AndroidGraphicFactory.createInstance(context.applicationContext)

                val mapView = MapView(context).apply {
                    mapScaleBar.isVisible = true
                    setBuiltInZoomControls(true)
                    setZoomLevelMin(2.toByte())
                    setZoomLevelMax(20.toByte())
                }

                val mapFile = MapFile(
                    Environment.getExternalStorageDirectory()
                        .resolve("maps/italy.map")
                )

                val tileCache: TileCache = AndroidUtil.createTileCache(
                    context,
                    "mapcache",
                    mapView.model.displayModel.tileSize,
                    1f,
                    mapView.model.frameBufferModel.overdrawFactor
                )

                val renderer = TileRendererLayer(
                    tileCache,
                    mapFile,
                    mapView.model.mapViewPosition,
                    AndroidGraphicFactory.INSTANCE
                ).apply {
                    setXmlRenderTheme(AssetsRenderTheme(context))
                }

                mapView.layerManager.layers.add(renderer)

                mapView.model.mapViewPosition.setCenter(LatLong(45.4642, 9.19))
                mapView.model.mapViewPosition.setZoomLevel(12.toByte())

                mapView
            },
            modifier = modifier
        )
    } else {
        // Messaggio se permesso negato
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            Text("Permesso di lettura memoria negato")
        }
    }
}**/

package com.example.mapsforgecomposeapp

import android.content.Context
import android.net.Uri
import android.os.Environment
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
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import java.io.File

@Composable
fun MapsforgeMapComposable(
    context: Context,
    modifier: Modifier = Modifier,
    mapFileName: String = "italy.map", // nome file nella cartella app-specific
    useFilePicker: Boolean = false     // se true, l’utente può scegliere il file
) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }

    // Launcher per aprire file picker (opzionale)
    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        selectedUri = uri
    }

    if (LocalInspectionMode.current) {
        // Preview
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Gray),
            contentAlignment = Alignment.Center
        ) {
            Text("Anteprima mappa")
        }
        return
    }

    // Determina il percorso del file
    val mapFile: File? = if (useFilePicker && selectedUri != null) {
        // Se utente ha scelto il file
        File(selectedUri!!.path ?: "")
    } else {
        // File in directory app-specific: non servono permessi runtime
        File(context.getExternalFilesDir("maps"), mapFileName)
    }

    if (useFilePicker && selectedUri == null) {
        // Mostra bottone per aprire il file picker
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Button(onClick = { pickerLauncher.launch(arrayOf("*/*")) }) {
                Text("Seleziona file mappa")
            }
        }
        return
    }

    if (mapFile == null || !mapFile.exists()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            Text("File mappa non trovato: ${mapFile?.absolutePath ?: "null"}")
        }
        return
    }

    // Carica la mappa con MapsForge
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
            mapView.model.mapViewPosition.setCenter(LatLong(45.4642, 9.19))
            mapView.model.mapViewPosition.setZoomLevel(12.toByte())

            mapView
        },
        modifier = modifier
    )
}

/** ultima vesione da debuggare su device**/