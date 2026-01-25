package it.fourSTL.PositionMarker

import android.content.ContentValues
import android.content.Context
import android.location.Location
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GpxUtils {

    fun saveTrackAsGpx(
        context: Context,
        trackPoints: List<Location>,
        fileName: String,
        trackWidthMeters: Float? = null // 🆕 Parametro opzionale
    ) {
        if (trackPoints.isEmpty()) {
            Toast.makeText(context, "No point to save.", Toast.LENGTH_SHORT).show()
            return
        }

        val gpxString = generateGpxString(trackPoints, fileName, trackWidthMeters)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.gpx")
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/gpx+xml")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { output ->
                        output.write(gpxString.toByteArray())
                    }

                    val widthInfo = trackWidthMeters?.let { " (width: ${it}m)" } ?: ""
                    Toast.makeText(
                        context,
                        "GPS track saved$widthInfo in Downloads/$fileName.gpx",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    throw Exception("Could not create file in MediaStore")
                }
            } else {
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }

                val file = File(downloadDir, "$fileName.gpx")
                FileOutputStream(file).use { output ->
                    output.write(gpxString.toByteArray())
                }

                val widthInfo = trackWidthMeters?.let { " (width: ${it}m)" } ?: ""
                Toast.makeText(
                    context,
                    "GPS track saved$widthInfo in Download/$fileName.gpx",
                    Toast.LENGTH_LONG
                ).show()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Save error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun generateGpxString(
        trackPoints: List<Location>,
        trackName: String,
        trackWidthMeters: Float? = null
    ): String {
        val iso8601Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val creationTime = iso8601Format.format(Date())

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"fourSTLPositionMarker\" ")
        sb.append("xmlns=\"http://www.topografix.com/GPX/1/1\" ")
        sb.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
        // 🆕 Aggiungi namespace custom per estensioni
        sb.append("xmlns:fourSTL=\"http://fourSTL.it/gpx/extensions/1.0\" ")
        sb.append("xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")

        sb.append("  <metadata>\n")
        sb.append("    <name>${escapeXml(trackName)}</name>\n")
        sb.append("    <time>$creationTime</time>\n")

        // 🆕 Aggiungi larghezza nei metadata come estensione
        trackWidthMeters?.let { width ->
            sb.append("    <extensions>\n")
            sb.append("      <fourSTL:trackWidth>$width</fourSTL:trackWidth>\n")
            sb.append("      <fourSTL:widthUnit>meters</fourSTL:widthUnit>\n")
            sb.append("    </extensions>\n")
        }

        sb.append("  </metadata>\n")

        sb.append("  <trk>\n")
        sb.append("    <name>${escapeXml(trackName)}</name>\n")

        // 🆕 Ripeti larghezza anche nel track (per compatibilità)
        trackWidthMeters?.let { width ->
            sb.append("    <extensions>\n")
            sb.append("      <fourSTL:trackWidth>$width</fourSTL:trackWidth>\n")
            sb.append("    </extensions>\n")
        }

        sb.append("    <trkseg>\n")

        trackPoints.forEach { point ->
            val pointTime = iso8601Format.format(Date(point.time))
            sb.append("      <trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\">\n")
            if (point.hasAltitude()) {
                sb.append("        <ele>${point.altitude}</ele>\n")
            }
            sb.append("        <time>$pointTime</time>\n")
            sb.append("      </trkpt>\n")
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")

        return sb.toString()
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
