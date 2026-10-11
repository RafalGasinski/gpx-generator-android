package pl.example.gpxgenerator.routing

import pl.example.gpxgenerator.model.GeoPoint

data class RouteRequest(
    val start: GeoPoint,
    val end: GeoPoint,
    val intermediates: List<GeoPoint>,
    val profile: String
)
sealed interface RouteHandoffResult {
    data object AcceptedByOsmAnd : RouteHandoffResult
    data class Failed(val reason: String) : RouteHandoffResult
}

/** Abstrakcja aplikacji, nie interfejs OsmAnd SDK. */
interface RoutingProvider {
    suspend fun handoffRoute(request: RouteRequest): RouteHandoffResult
}
