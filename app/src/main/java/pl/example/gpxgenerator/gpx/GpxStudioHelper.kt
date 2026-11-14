package pl.example.gpxgenerator.gpx

import android.content.Context
import android.content.Intent
import android.net.Uri
import pl.example.gpxgenerator.generator.RouteCandidate

object GpxStudioHelper {

    fun openInGpxStudio(context: Context, candidate: RouteCandidate): String {
        return runCatching {
            val file = GpxExporter.saveGpxToPublicDownloads(context, candidate, "trasa_motocyklowa.gpx")

            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gpx.studio")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)

            "Zapisano trasę w Pobrane/Trasy GPX/${file.name}. W gpx.studio wybierz Plik -> Otwórz..."
        }.getOrElse { err ->
            "Błąd eksportu do gpx.studio: ${err.message}"
        }
    }
}
