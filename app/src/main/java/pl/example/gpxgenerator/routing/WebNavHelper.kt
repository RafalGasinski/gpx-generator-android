package pl.example.gpxgenerator.routing

import android.content.Context
import android.content.Intent
import android.net.Uri
import pl.example.gpxgenerator.generator.RouteCandidate
import pl.example.gpxgenerator.gpx.GpxExporter

data class WebToolInfo(
    val id: String,
    val displayName: String,
    val url: String,
    val isPrimary: Boolean = false
)

object WebNavHelper {

    val WEB_TOOLS = listOf(
        WebToolInfo("gpx_studio", "gpx.studio", "https://gpx.studio", isPrimary = true),
        WebToolInfo("mapy_cz_web", "Mapy.cz Web", "https://en.mapy.cz/gpx"),
        WebToolInfo("kurviger_web", "Kurviger Web", "https://kurviger.de"),
        WebToolInfo("tomtom_mydrive", "TomTom MyDrive", "https://mydrive.tomtom.com"),
        WebToolInfo("calimoto_web", "Calimoto Web", "https://calimoto.com"),
        WebToolInfo("garmin_explore_web", "Garmin Explore", "https://explore.garmin.com")
    )

    fun openInWebTool(context: Context, tool: WebToolInfo, candidate: RouteCandidate): String {
        return runCatching {
            val file = GpxExporter.saveGpxToPublicDownloads(context, candidate, "trasa_motocyklowa.gpx")

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(tool.url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            "Zapisano trasę w Pobrane/Trasy GPX/${file.name}. Otwarto ${tool.displayName} w przeglądarce..."
        }.getOrElse { err ->
            "Błąd eksportu do ${tool.displayName}: ${err.message}"
        }
    }
}
