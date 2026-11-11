package pl.example.gpxgenerator.generator

import pl.example.gpxgenerator.model.GeoPoint
import pl.example.gpxgenerator.model.OsmRoadGraph
import pl.example.gpxgenerator.model.RoutePreferences
import pl.example.gpxgenerator.network.OverpassClient
import java.io.IOException

data class RouteCandidate(
    val start: GeoPoint,
    val end: GeoPoint,
    val roadPoints: List<GeoPoint>,
    val preferences: RoutePreferences,
    val estimatedDistanceKm: Double,
    val trackPointCount: Int
)

object RouteCandidateGenerator {

    /**
     * Główny algorytm trasowania po realnej siatce dróg z OSM:
     * 1. Pobiera drogi w promieniu/bounding boxie z Overpass API.
     * 2. Buduje graf w pamięci z wyważonymi kosztami dróg (omijanie A/S/DK, priorytet dróg lokalnych).
     * 3. Wyszukuje najbliższe węzły dróg dla startu i mety.
     * 4. Uruchamia algorytm A* Pathfinder wyznaczający rzeczywisty ślad drogowo-geograficzny.
     */
    fun generateRealRoadRoute(
        start: GeoPoint,
        end: GeoPoint,
        preferences: RoutePreferences
    ): RouteCandidate {
        val overpassData = OverpassClient.fetchRoadsInBoundingBox(start, end)
        require(overpassData.ways.isNotEmpty()) {
            "Nie znaleziono dróg w wskazanym obszarze. Sprawdź punkty startu i mety."
        }

        val graph = OsmRoadGraph.build(overpassData, preferences)

        val startNodeId = graph.findNearestNode(start)
            ?: throw IOException("Nie udało się zlokalizować najbliższej drogi dla punktu startowego.")
        val endNodeId = graph.findNearestNode(end)
            ?: throw IOException("Nie udało się zlokalizować najbliższej drogi dla punktu docelowego.")

        val pathResult = AStarRouter.findPath(graph, startNodeId, endNodeId)
            ?: throw IOException("Nie udało się wyznaczyć trasy bocznej po drogach utwardzonych w tym obszarze. Spróbuj zmienić punkty lub włączyć opcję szutru.")

        val fullRoadPoints = listOf(start) + pathResult.pathPoints + listOf(end)

        return RouteCandidate(
            start = start,
            end = end,
            roadPoints = fullRoadPoints,
            preferences = preferences,
            estimatedDistanceKm = pathResult.totalDistanceKm,
            trackPointCount = fullRoadPoints.size
        )
    }
}
