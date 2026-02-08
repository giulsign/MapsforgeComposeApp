package it.fourSTL.PositionMarker

import android.content.Context
import android.net.Uri
import org.mapsforge.core.model.LatLong
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

data class GpxData(
    val trackPoints: List<LatLong>,
    val trackWidthMeters: Float? = null,
    val trackName: String? = null
)

object GpxParser {
    fun parse(context: Context, uri: Uri): GpxData {
        val trackPoints = mutableListOf<LatLong>()
        var trackWidthMeters: Float? = null
        var trackName: String? = null
        var stream: InputStream? = null

        try {
            stream = context.contentResolver.openInputStream(uri)
            if (stream == null) return GpxData(emptyList())

            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(stream, null)

            var eventType = parser.eventType
            var insideExtensions = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            // Leggi punti traccia
                            "trkpt" -> {
                                val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                                val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                                if (lat != null && lon != null) {
                                    trackPoints.add(LatLong(lat, lon))
                                }
                            }

                            // READ NAME
                            "name" -> {
                                if (trackName == null) {
                                    trackName = parser.nextText()
                                }
                            }

                            // ENTER EXTENSION SESSION
                            "extensions" -> {
                                insideExtensions = true
                            }

                            // READ WIDTH
                            "trackWidth" -> {
                                if (insideExtensions && trackWidthMeters == null) {
                                    try {
                                        trackWidthMeters = parser.nextText().toFloatOrNull()
                                    } catch (e: Exception) {
                                    }
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if (parser.name == "extensions") {
                            insideExtensions = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return GpxData(emptyList())
        } finally {
            stream?.close()
        }

        return GpxData(
            trackPoints = trackPoints,
            trackWidthMeters = trackWidthMeters,
            trackName = trackName
        )
    }

    @Deprecated("Use parse() that returns GpxData", ReplaceWith("parse(context, uri).trackPoints"))
    fun parsePoints(context: Context, uri: Uri): List<LatLong> {
        return parse(context, uri).trackPoints
    }
}