package pl.example.gpxgenerator.generator

import pl.example.gpxgenerator.model.GeoPoint
import pl.example.gpxgenerator.model.OsmRoadGraph
import java.util.PriorityQueue
import kotlin.math.cos
import kotlin.math.sqrt

data class AStarResult(
    val pathPoints: List<GeoPoint>,
    val totalDistanceKm: Double
)

private data class NodeScore(val nodeId: Long, val fScore: Double) : Comparable<NodeScore> {
    override fun compareTo(other: NodeScore): Int = fScore.compareTo(other.fScore)
}

object AStarRouter {

    fun findPath(
        graph: OsmRoadGraph,
        startNodeId: Long,
        endNodeId: Long
    ): AStarResult? {
        val targetPoint = graph.nodes[endNodeId] ?: return null
        val startPoint = graph.nodes[startNodeId] ?: return null

        val avgLatRad = Math.toRadians((startPoint.latitude + targetPoint.latitude) / 2.0)
        val cosAvgLat = cos(avgLatRad)

        val targetLat = targetPoint.latitude
        val targetLon = targetPoint.longitude

        val gScore = HashMap<Long, Double>(graph.nodes.size / 2)
        val cameFrom = HashMap<Long, Long>(graph.nodes.size / 2)
        val closedSet = HashSet<Long>(graph.nodes.size / 2)

        val openSet = PriorityQueue<NodeScore>()

        gScore[startNodeId] = 0.0
        val initialH = fastFlatDistanceKm(startPoint.latitude, startPoint.longitude, targetLat, targetLon, cosAvgLat)
        openSet.add(NodeScore(startNodeId, initialH))

        while (openSet.isNotEmpty()) {
            val current = openSet.poll() ?: break
            val currNodeId = current.nodeId

            if (currNodeId == endNodeId) {
                return reconstructPath(graph, cameFrom, endNodeId, cosAvgLat)
            }

            if (!closedSet.add(currNodeId)) {
                continue
            }

            val currentG = gScore[currNodeId] ?: continue

            val edges = graph.adjacency[currNodeId] ?: emptyList()
            for (edge in edges) {
                val neighborId = edge.toNodeId
                if (neighborId in closedSet) continue

                val tentativeG = currentG + edge.weightedCost

                if (tentativeG < (gScore[neighborId] ?: Double.MAX_VALUE)) {
                    cameFrom[neighborId] = currNodeId
                    gScore[neighborId] = tentativeG

                    val neighborPoint = graph.nodes[neighborId]
                    if (neighborPoint != null) {
                        val h = fastFlatDistanceKm(neighborPoint.latitude, neighborPoint.longitude, targetLat, targetLon, cosAvgLat)
                        openSet.add(NodeScore(neighborId, tentativeG + h))
                    }
                }
            }
        }

        return null
    }

    private fun reconstructPath(
        graph: OsmRoadGraph,
        cameFrom: Map<Long, Long>,
        endNodeId: Long,
        cosAvgLat: Double
    ): AStarResult {
        val pathNodeIds = mutableListOf<Long>()
        var curr: Long? = endNodeId

        while (curr != null) {
            pathNodeIds.add(curr)
            curr = cameFrom[curr]
        }

        pathNodeIds.reverse()

        val points = pathNodeIds.mapNotNull { graph.nodes[it] }

        var totalDistKm = 0.0
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            totalDistKm += fastFlatDistanceKm(p1.latitude, p1.longitude, p2.latitude, p2.longitude, cosAvgLat)
        }

        return AStarResult(points, totalDistKm)
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
}
