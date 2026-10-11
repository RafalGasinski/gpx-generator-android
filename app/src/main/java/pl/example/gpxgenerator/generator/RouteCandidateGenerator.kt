package pl.example.gpxgenerator.generator

import pl.example.gpxgenerator.model.GeoPoint

data class RouteSettings(val allowUnpaved: Boolean = false, val preferShortest: Boolean = true)
data class RouteCandidate(
    val start: GeoPoint,
    val end: GeoPoint,
    val controlPoints: List<GeoPoint>,
    val settings: RouteSettings
)

object RouteCandidateGenerator {
    // Etap migracji: zachowujemy punkty start/meta. Kolejny krok to algorytm wyboru
    // punktów pośrednich; drogi pomiędzy punktami ma wyznaczać OsmAnd.
    fun generate(start: GeoPoint, end: GeoPoint, settings: RouteSettings) =
        RouteCandidate(start, end, emptyList(), settings)
}
