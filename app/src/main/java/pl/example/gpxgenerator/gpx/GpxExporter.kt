package pl.example.gpxgenerator.gpx

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import pl.example.gpxgenerator.generator.RouteCandidate
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GpxExporter {

    fun exportToGpxString(candidate: RouteCandidate, name: String = "Trasa Motocyklowa"): String {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val nowStr = isoFormat.format(Date())
        val escapedName = xmlEscape(name)

        val start = candidate.start
        val end = candidate.end

        val wptXml = """
  <wpt lat="${start.latitude}" lon="${start.longitude}">
    <name>Start</name>
    <sym>Flag, Green</sym>
  </wpt>
  <wpt lat="${end.latitude}" lon="${end.longitude}">
    <name>Meta</name>
    <sym>Flag, Red</sym>
  </wpt>
        """.trimIndent()

        val rteptXml = """
    <rtept lat="${start.latitude}" lon="${start.longitude}">
      <name>Start</name>
    </rtept>
    <rtept lat="${end.latitude}" lon="${end.longitude}">
      <name>Meta</name>
    </rtept>
        """.trimIndent()

        val trkptXml = candidate.roadPoints.joinToString("\n") { p ->
            "      <trkpt lat=\"${p.latitude}\" lon=\"${p.longitude}\" />"
        }

        return """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="GPX Generator Android"
     xmlns="http://www.topografix.com/GPX/1/1"
     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
     xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">
  <metadata>
    <name>$escapedName</name>
    <desc>Trasa motocyklowa wyznaczona po drogach bocznych (bez A/S/DK)</desc>
    <time>$nowStr</time>
  </metadata>

$wptXml

  <rte>
    <name>$escapedName</name>
$rteptXml
  </rte>

  <trk>
    <name>$escapedName</name>
    <trkseg>
$trkptXml
    </trkseg>
  </trk>
</gpx>""".trimIndent()
    }

    fun saveGpxFile(context: Context, candidate: RouteCandidate, fileName: String = "trasa_motocyklowa.gpx"): Pair<File, Uri> {
        val gpxContent = exportToGpxString(candidate)
        val dir = File(context.cacheDir, "gpx_exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(gpxContent, Charsets.UTF_8)

        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        return Pair(file, uri)
    }

    fun saveGpxToPublicDownloads(context: Context, candidate: RouteCandidate, fileName: String = "trasa_motocyklowa.gpx"): File {
        val gpxContent = exportToGpxString(candidate)
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        val dir = File(baseDir, "Trasy_GPX").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(gpxContent, Charsets.UTF_8)
        return file
    }

    fun shareGpxFile(context: Context, uri: Uri, title: String = "Udostępnij trasę GPX") {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun xmlEscape(v: String) = v
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
