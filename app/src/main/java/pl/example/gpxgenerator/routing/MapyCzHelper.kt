package pl.example.gpxgenerator.routing

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import pl.example.gpxgenerator.generator.RouteCandidate
import pl.example.gpxgenerator.gpx.GpxExporter

object MapyCzHelper {

    private const val MAPY_CZ_PACKAGE = "cz.seznam.mapy"

    fun isMapyCzInstalled(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(MAPY_CZ_PACKAGE, PackageManager.GET_ACTIVITIES)
            true
        }.getOrDefault(false)
    }

    fun openInMapyCz(context: Context, candidate: RouteCandidate, gpxUri: Uri): String {
        return if (isMapyCzInstalled(context)) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(gpxUri, "application/gpx+xml")
                setPackage(MAPY_CZ_PACKAGE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            runCatching {
                context.startActivity(intent)
                "Trasa została pomyślnie otwarta w aplikacji Mapy.cz."
            }.getOrElse {
                openMapyCzWebFallback(context, candidate)
            }
        } else {
            openMapyCzWebFallback(context, candidate)
        }
    }

    private fun openMapyCzWebFallback(context: Context, candidate: RouteCandidate): String {
        return runCatching {
            val file = GpxExporter.saveGpxToPublicDownloads(context, candidate, "trasa_motocyklowa.gpx")

            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://en.mapy.cz/gpx")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)

            "Zapisano GPX w Pobranych/Trasy GPX/${file.name}. Otwarto https://en.mapy.cz/gpx w przeglądarce..."
        }.getOrElse { err ->
            "Błąd eksportu do Mapy.cz: ${err.message}"
        }
    }
}
