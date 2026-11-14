package pl.example.gpxgenerator.model

import pl.example.gpxgenerator.network.OverpassResponseData
import kotlin.math.cos
import kotlin.math.sqrt

data class OsmGraphEdge(
    val fromNodeId: Long,
    val toNodeId: Long,
    val distanceKm: Double,
    val costMultiplier: Double,
    val highwayType: String
) {
    val weightedCost: Double get() = distanceKm * costMultiplier
}

class OsmRoadGraph(
    val nodes: Map<Long, GeoPoint>,
    val adjacency: Map<Long, List<OsmGraphEdge>>
) {

    fun findNearestNodeCandidates(target: GeoPoint, count: Int = 5): List<Long> {
        if (nodes.isEmpty() || adjacency.isEmpty()) return emptyList()
        val targetLat = target.latitude
        val targetLon = target.longitude
        val cosLat = cos(Math.toRadians(targetLat))

        val candidateList = ArrayList<Pair<Long, Double>>()
        for ((id, edges) in adjacency) {
            if (edges.isEmpty()) continue
            val point = nodes[id] ?: continue
            val dLat = point.latitude - targetLat
            val dLon = (point.longitude - targetLon) * cosLat
            val sqDist = dLat * dLat + dLon * dLon
            candidateList.add(id to sqDist)
        }
        candidateList.sortBy { it.second }
        return candidateList.take(count).map { it.first }
    }

    fun findNearestNode(target: GeoPoint): Long? {
        return findNearestNodeCandidates(target, 1).firstOrNull()
    }

    companion object {
        fun build(responseData: OverpassResponseData, preferences: RoutePreferences): OsmRoadGraph {
            val nodes = responseData.nodes
            val adjMap = HashMap<Long, MutableList<OsmGraphEdge>>(nodes.size)

            val avgLat = if (nodes.isNotEmpty()) nodes.values.first().latitude else 50.0
            val cosAvgLat = cos(Math.toRadians(avgLat))

            for (way in responseData.ways) {
                val costMult = calculateCostMultiplier(way.highwayType, way.surface, preferences)
                if (costMult >= 9999.0) continue // Odrzucanie dróg niedozwolonych

                val nodeIds = way.nodeIds
                for (i in 0 until nodeIds.size - 1) {
                    val idA = nodeIds[i]
                    val idB = nodeIds[i + 1]

                    val pointA = nodes[idA] ?: continue
                    val pointB = nodes[idB] ?: continue

                    val distKm = fastFlatDistanceKm(pointA.latitude, pointA.longitude, pointB.latitude, pointB.longitude, cosAvgLat)

                    val edgeAB = OsmGraphEdge(idA, idB, distKm, costMult, way.highwayType)
                    adjMap.getOrPut(idA) { ArrayList(4) }.add(edgeAB)

                    if (!way.isOneway) {
                        val edgeBA = OsmGraphEdge(idB, idA, distKm, costMult, way.highwayType)
                        adjMap.getOrPut(idB) { ArrayList(4) }.add(edgeBA)
                    }
                }
            }

            return OsmRoadGraph(nodes, adjMap)
        }

        private fun fastFlatDistanceKm(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double,
            cosAvgLat: Double
        ): Double {
            val dLat = (lat2 - lat1) * 111.139
            val dLon = (lon2 - lon1) * 111.139 * cosAvgLat
            return sqrt(dLat * dLat + dLon * dLon)
        }

        private fun calculateCostMultiplier(
            highwayType: String,
            surface: String?,
            preferences: RoutePreferences
        ): Double {
            val isUnpavedSurface = surface in listOf("unpaved", "gravel", "dirt", "earth", "fine_gravel", "compacted", "ground", "grass")

            return when (highwayType) {
                "motorway", "motorway_link" -> 1000.0 // Autostrady A (Omijane)
                "trunk", "trunk_link" -> 500.0       // Ekspresówki S (Omijane)
                "primary", "primary_link" -> if (preferences.avoidHighways) 50.0 else 2.0 // DK (Omijane)
                "secondary", "secondary_link" -> 3.0  // DW
                "tertiary", "tertiary_link" -> 1.0    // Powiatowe (Ideal)
                "unclassified" -> 1.0                // Gminne/Lokalne (Ideal)
                "residential" -> 1.2                 // Osiedlowe
                "living_street" -> 1.5
                "track" -> {
                    if (preferences.allowGravel) 1.2 else 100.0
                }
                "path", "bridleway" -> 10000.0
                "pedestrian", "footway", "steps", "cycleway" -> 10000.0
                else -> {
                    if (isUnpavedSurface) {
                        if (preferences.allowGravel) 1.5 else 100.0
                    } else {
                        1.5
                    }
                }
            }
        }
    }
}
