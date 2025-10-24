package it.fourSTL.PositionMarker

import android.content.Context
import android.net.Uri
import org.mapsforge.core.model.LatLong
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/**
 * Un oggetto di utility per analizzare (fare il "parsing") dei file GPX.
 */
object GpxParser {

    /**
     * Legge un file GPX da un URI (fornito dal selettore di file) e restituisce una lista di coordinate.
     *
     * @param context Il contesto dell'applicazione.
     * @param uri L'URI del file GPX selezionato.
     * @return Una lista di oggetti LatLong che rappresentano il percorso, o una lista vuota in caso di errore.
     */
    fun parse(context: Context, uri: Uri): List<LatLong> {
        val trackPoints = mutableListOf<LatLong>()
        var stream: InputStream? = null
        try {
            // Apre un flusso di dati dal file scelto dall'utente
            stream = context.contentResolver.openInputStream(uri)
            if (stream == null) return emptyList()

            // Inizializza il parser XML
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(stream, null)

            // Scorre il file XML alla ricerca dei tag "trkpt" (track point)
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
            e.printStackTrace() // Stampa l'errore nel Logcat per debug
            // In caso di errore (file non valido, ecc.), restituisce una lista vuota
            return emptyList()
        } finally {
            // Chiude il flusso in modo sicuro
            stream?.close()
        }
        return trackPoints
    }
}