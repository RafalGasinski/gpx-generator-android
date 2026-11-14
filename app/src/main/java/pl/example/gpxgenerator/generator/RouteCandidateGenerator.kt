package pl.example.gpxgenerator.generator

import android.content.Context
import pl.example.gpxgenerator.model.GeoPoint
import pl.example.gpxgenerator.model.OsmRoadGraph
import pl.example.gpxgenerator.model.RoutePreferences
import pl.example.gpxgenerator.network.OverpassClient
import java.io.IOException
import kotlin.math.max

data class RouteCandidate(
    val start: GeoPoint,
    val end: GeoPoint,
    val roadPoints: List<GeoPoint>,
    val preferences: RoutePreferences,
    val estimatedDistanceKm: Double,
    val trackPointCount: Int,
    val fromCache: Boolean = false,
    val executionTimeMs: Long = 0L,
    val fetchTimeMs: Long = 0L,
    val routerTimeMs: Long = 0L,
    val payloadSizeBytes: Long = 0L,
    val ramUsedMb: Double = 0.0
)

object RouteCandidateGenerator {

    fun generateRealRoadRoute(
        context: Context,
        start: GeoPoint,
        end: GeoPoint,
        preferences: RoutePreferences,
        onProgress: (String) -> Unit = {}
    ): RouteCandidate {
        val runtime = Runtime.getRuntime()
        val ramBefore = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0)

        val t0 = System.currentTimeMillis()
        val overpassData = OverpassClient.fetchRoadsInBoundingBox(context, start, end, onProgress)
        val t1 = System.currentTimeMillis()

        if (overpassData.ways.isEmpty()) {
            throw IOException("Pusta siatka dróg z Overpass (Nodes: ${overpassData.nodes.size}, Ways: 0). Start: (${start.latitude}, ${start.longitude}), Meta: (${end.latitude}, ${end.longitude})")
        }

        onProgress("🏗️ Budowanie grafu dróg (${overpassData.nodes.size} węzłów, ${overpassData.ways.size} odcinków)...")
        val graph = OsmRoadGraph.build(overpassData, preferences)

        val startCandidates = graph.findNearestNodeCandidates(start, 5)
        val endCandidates = graph.findNearestNodeCandidates(end, 5)

        if (startCandidates.isEmpty() || endCandidates.isEmpty()) {
            throw IOException("Brak kandydujących węzłów w grafie (Start: ${startCandidates.size}, Meta: ${endCandidates.size}). Sprawdź punkty na mapie.")
        }

        onProgress("🔍 Wyznaczanie optymalnej ścieżki A* po drogach bocznych...")

        var bestPathResult: AStarResult? = null
        for (sId in startCandidates) {
            for (eId in endCandidates) {
                val path = AStarRouter.findPath(graph, sId, eId)
                if (path != null) {
                    bestPathResult = path
                    break
                }
            }
            if (bestPathResult != null) break
        }

        val pathResult = bestPathResult
            ?: throw IOException("A* Router: brak połączenia w grafie po drogach bocznych. Spróbuj zmienić punkty lub włączyć opcję szutru.")

        val t2 = System.currentTimeMillis()
        val ramAfter = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0)

        val fullRoadPoints = listOf(start) + pathResult.pathPoints + listOf(end)
        val totalTime = t2 - t0
        val fetchTime = t1 - t0
        val routerTime = t2 - t1

        val totalEdges = graph.adjacency.values.sumOf { it.size }
        val graphMemMb = (graph.nodes.size * 64.0 + totalEdges * 48.0) / (1024.0 * 1024.0)
        val heapDeltaMb = max(0.1, ramAfter - ramBefore)
        val ramUsed = if (heapDeltaMb > 50.0) graphMemMb else heapDeltaMb

        onProgress("✅ Sukces! Wyznaczono trasę w %.2f s.".format(totalTime / 1000.0))

        return RouteCandidate(
            start = start,
            end = end,
            roadPoints = fullRoadPoints,
            preferences = preferences,
            estimatedDistanceKm = pathResult.totalDistanceKm,
            trackPointCount = fullRoadPoints.size,
            fromCache = overpassData.fromCache,
            executionTimeMs = totalTime,
            fetchTimeMs = fetchTime,
            routerTimeMs = routerTime,
            payloadSizeBytes = overpassData.payloadSizeBytes,
            ramUsedMb = ramUsed
        )
    }
}
