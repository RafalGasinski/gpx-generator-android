package pl.example.gpxgenerator.routing

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

data class NavAppInfo(
    val packageName: String,
    val displayName: String,
    val isOsmAnd: Boolean = false
)

object NavAppDetector {

    val SUPPORTED_NAV_APPS = listOf(
        NavAppInfo("net.osmand", "OsmAnd", isOsmAnd = true),
        NavAppInfo("net.osmand.plus", "OsmAnd+", isOsmAnd = true),
        NavAppInfo("net.osmand.dev", "OsmAnd Dev", isOsmAnd = true),
        NavAppInfo("cz.seznam.mapy", "Mapy.cz"),
        NavAppInfo("de.kurviger.k3", "Kurviger K3"),
        NavAppInfo("de.kurviger.app", "Kurviger"),
        NavAppInfo("com.calimoto.calimoto", "Calimoto"),
        NavAppInfo("com.royalenfield.riderapp", "Royal Enfield"),
        NavAppInfo("com.tomtom.gns.androidapp", "TomTom GO"),
        NavAppInfo("com.garmin.android.apps.explore", "Garmin Explore"),
        NavAppInfo("menion.android.locus", "Locus Map"),
        NavAppInfo("se.detecht.android", "Detecht")
    )

    fun getInstalledNavApps(context: Context): List<NavAppInfo> {
        val pm = context.packageManager
        val installed = mutableListOf<NavAppInfo>()

        for (app in SUPPORTED_NAV_APPS) {
            runCatching {
                pm.getPackageInfo(app.packageName, PackageManager.GET_ACTIVITIES)
                installed.add(app)
            }
        }

        return installed.sortedWith(
            compareByDescending<NavAppInfo> { it.isOsmAnd }.thenBy { it.displayName }
        )
    }

    fun openInApp(context: Context, appInfo: NavAppInfo, gpxUri: Uri): String {
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(gpxUri, "application/gpx+xml")
                setPackage(appInfo.packageName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Otwieranie trasy GPX w aplikacji ${appInfo.displayName}..."
        }.getOrElse { err ->
            "Błąd uruchamiania ${appInfo.displayName}: ${err.message}"
        }
    }
}
