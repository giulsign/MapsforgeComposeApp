package com.example.mapsforgecomposeapp

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
import org.mapsforge.map.model.Model

@Composable
fun MapsforgeMapComposable(
    context: Context,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = {
            // Inizializzazione grafica Mapsforge
            AndroidGraphicFactory.createInstance(context.applicationContext)

            val mapView = MapView(context).apply {
                // Configurazioni base
                mapScaleBar.isVisible = true
                setBuiltInZoomControls(true)
                setZoomLevelMin(2.toByte())
                setZoomLevelMax(20.toByte())
            }

            // Carico il file .map
            val mapFile = MapFile(
                Environment.getExternalStorageDirectory()
                    .resolve("app/main/assets/italy.map")
            )

            // Creo la cache tile
            // Creo la cache tile
            val tileCache: TileCache = AndroidUtil.createTileCache(
                context,
                "mapcache",
                mapView.model.displayModel.tileSize,
                1f,
                mapView.model.frameBufferModel.overdrawFactor
            )


            // Creo il renderer con il tema custom
            val renderer = TileRendererLayer(
                tileCache,
                mapFile,
                mapView.model.mapViewPosition,
                AndroidGraphicFactory.INSTANCE
            ).apply {
                setXmlRenderTheme(AssetsRenderTheme(context))
            }

            mapView.layerManager.layers.add(renderer)

            // Centro e zoom iniziale
            mapView.model.mapViewPosition.setCenter(LatLong(45.4642, 9.19)) // Milano
            mapView.model.mapViewPosition.setZoomLevel(12.toByte())

            mapView
        },
        modifier = modifier
    )
}
