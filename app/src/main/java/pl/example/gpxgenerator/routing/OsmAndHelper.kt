package pl.example.gpxgenerator.routing

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import net.osmand.aidlapi.IOsmAndAidlInterface
import pl.example.gpxgenerator.model.GeoPoint

object OsmAndHelper : RoutingProvider {

    private val KNOWN_PACKAGES = listOf(
        "net.osmand.plus",
        "net.osmand",
        "net.osmand.dev"
    )

    private const val AIDL_ACTION = "net.osmand.aidl.OsmAndAidlService"

    private var aidlService: IOsmAndAidlInterface? = null
    private var boundPackageName: String? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            aidlService = IOsmAndAidlInterface.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            aidlService = null
            boundPackageName = null
        }
    }

    fun findInstalledOsmAndPackage(context: Context): String? {
        val pm = context.packageManager
        for (pkg in KNOWN_PACKAGES) {
            runCatching {
                pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                return pkg
            }
        }
        return null
    }

    fun bindAidlService(context: Context): Boolean {
        val targetPackage = findInstalledOsmAndPackage(context) ?: return false
        if (aidlService != null && boundPackageName == targetPackage) return true

        val intent = Intent(AIDL_ACTION).apply {
            setPackage(targetPackage)
        }
        return runCatching {
            val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (bound) boundPackageName = targetPackage
            bound
        }.getOrDefault(false)
    }

    fun unbindAidlService(context: Context) {
        if (boundPackageName != null) {
            runCatching { context.unbindService(serviceConnection) }
            aidlService = null
            boundPackageName = null
        }
    }

    override fun checkOsmAndStatus(context: Context): OsmAndStatus {
        val pkg = findInstalledOsmAndPackage(context)
        return if (pkg != null) {
            val isConnected = aidlService != null
            OsmAndStatus.Installed(packageName = pkg, isAidlConnected = isConnected)
        } else {
            OsmAndStatus.NotInstalled
        }
    }

    override suspend fun handoffRoute(
        context: Context,
        request: RouteRequest,
        gpxUri: Uri?
    ): RouteHandoffResult {
        val targetPackage = findInstalledOsmAndPackage(context)
            ?: return RouteHandoffResult.Failed("Brak zainstalowanej aplikacji OsmAnd (standard, +, dev). Zainstaluj OsmAnd ze Sklepu Play.")

        if (gpxUri != null) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(gpxUri, "application/gpx+xml")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage(targetPackage)
            }

            return runCatching {
                context.startActivity(intent)
                RouteHandoffResult.Success(
                    method = "Plik GPX (FileProvider)",
                    message = "Trasa została pomyślnie przekazana do OsmAnd ($targetPackage)."
                )
            }.getOrElse {
                openOsmAndNavigateIntent(context, targetPackage, request)
            }
        } else {
            return openOsmAndNavigateIntent(context, targetPackage, request)
        }
    }

    private fun openOsmAndNavigateIntent(
        context: Context,
        targetPackage: String,
        request: RouteRequest
    ): RouteHandoffResult {
        val uriStr = buildString {
            append("osmand.api://navigate?")
            append("start_lat=").append(request.start.latitude)
            append("&start_lon=").append(request.start.longitude)
            append("&dest_lat=").append(request.end.latitude)
            append("&dest_lon=").append(request.end.longitude)
            append("&profile=motorcycle")
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
            setPackage(targetPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return runCatching {
            context.startActivity(intent)
            RouteHandoffResult.Success(
                method = "Intent Navigation",
                message = "Uruchomiono nawigację motocyklową w OsmAnd ($targetPackage)."
            )
        }.getOrElse { err ->
            RouteHandoffResult.Failed("Nie udało się uruchomić OsmAnd: ${err.message}")
        }
    }
}
