package it.fourSTL.PositionMarker

import android.content.Context
import android.net.Uri
import org.mapsforge.core.model.LatLong
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

object GpxParser {

    fun parse(context: Context, uri: Uri): List<LatLong> {
        val trackPoints = mutableListOf<LatLong>()
        var stream: InputStream? = null
        try {
                        stream = context.contentResolver.openInputStream(uri)
            if (stream == null) return emptyList()


            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(stream, null)


            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "trkpt") {
                    // Quando trova un "trkpt", ne estrae la latitudine e la longitudine
                    val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                    val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()

                    if (lat != null && lon != null) {
                        trackPoints.add(LatLong(lat, lon))
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()

            return emptyList()
        } finally {

            stream?.close()
        }
        return trackPoints
    }
}