package pl.example.gpxgenerator.model

import pl.example.gpxgenerator.network.OverpassResponseData

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

    fun findNearestNode(target: GeoPoint): Long? {
        if (nodes.isEmpty()) return null
        var minDistance = Double.MAX_VALUE
        var nearestId: Long? = null

        for ((id, point) in nodes) {
            val dist = target.distanceTo(point)
            if (dist < minDistance) {
                minDistance = dist
                nearestId = id
            }
        }
        return nearestId
    }

    companion object {
        fun build(responseData: OverpassResponseData, preferences: RoutePreferences): OsmRoadGraph {
            val nodes = responseData.nodes
            val adjMap = mutableMapOf<Long, MutableList<OsmGraphEdge>>()

            for (way in responseData.ways) {
                val costMult = calculateCostMultiplier(way.highwayType, way.surface, preferences)
                if (costMult >= 9999.0) continue // Skip prohibited road types completely

                val nodeIds = way.nodeIds
                for (i in 0 until nodeIds.size - 1) {
                    val idA = nodeIds[i]
                    val idB = nodeIds[i + 1]

                    val pointA = nodes[idA] ?: continue
                    val pointB = nodes[idB] ?: continue

                    val distKm = pointA.distanceTo(pointB)

                    val edgeAB = OsmGraphEdge(idA, idB, distKm, costMult, way.highwayType)
                    adjMap.getOrPut(idA) { mutableListOf() }.add(edgeAB)

                    // Forward and reverse edges (unless oneway)
                    if (!way.isOneway) {
                        val edgeBA = OsmGraphEdge(idB, idA, distKm, costMult, way.highwayType)
                        adjMap.getOrPut(idB) { mutableListOf() }.add(edgeBA)
                    }
                }
            }

            return OsmRoadGraph(nodes, adjMap)
        }

        private fun calculateCostMultiplier(
            highwayType: String,
            surface: String?,
            preferences: RoutePreferences
        ): Double {
            val isUnpavedSurface = surface in listOf("unpaved", "gravel", "dirt", "earth", "fine_gravel", "compacted", "ground", "grass")

            return when (highwayType) {
                "motorway", "motorway_link" -> 1000.0 // A
                "trunk", "trunk_link" -> 500.0       // S
                "primary", "primary_link" -> if (preferences.avoidHighways) 50.0 else 2.0 // DK
                "secondary", "secondary_link" -> 3.0  // DW
                "tertiary", "tertiary_link" -> 1.0    // Powiatowe (Ideal)
                "unclassified" -> 1.0                // Gminne/Lokalne (Ideal)
                "residential" -> 1.2                 // Osiedlowe/Dojazdowe
                "living_street" -> 1.5
                "track" -> {
                    if (preferences.allowGravel) 1.2 else 100.0
                }
                "path", "bridleway" -> 10000.0 // Prohibited
                "pedestrian", "footway", "steps", "cycleway" -> 10000.0 // Prohibited
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
