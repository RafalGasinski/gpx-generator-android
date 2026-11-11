package pl.example.gpxgenerator.routing

import android.content.Context
import android.net.Uri
import pl.example.gpxgenerator.model.GeoPoint

data class RouteRequest(
    val start: GeoPoint,
    val end: GeoPoint,
    val intermediates: List<GeoPoint> = emptyList(),
    val profile: String = "motorcycle"
)

sealed interface OsmAndStatus {
    data class Installed(val packageName: String, val isAidlConnected: Boolean) : OsmAndStatus
    data object NotInstalled : OsmAndStatus
}

sealed interface RouteHandoffResult {
    data class Success(val method: String, val message: String) : RouteHandoffResult
    data class Failed(val reason: String) : RouteHandoffResult
}

interface RoutingProvider {
    fun checkOsmAndStatus(context: Context): OsmAndStatus
    suspend fun handoffRoute(context: Context, request: RouteRequest, gpxUri: Uri? = null): RouteHandoffResult
}
