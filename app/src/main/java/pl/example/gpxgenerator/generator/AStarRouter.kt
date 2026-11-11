package pl.example.gpxgenerator.generator

import pl.example.gpxgenerator.model.GeoPoint
import pl.example.gpxgenerator.model.OsmRoadGraph
import java.util.PriorityQueue

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

        val gScore = mutableMapOf<Long, Double>()
        val cameFrom = mutableMapOf<Long, Long>()

        val openSet = PriorityQueue<NodeScore>()

        gScore[startNodeId] = 0.0
        val initialH = graph.nodes[startNodeId]?.distanceTo(targetPoint) ?: 0.0
        openSet.add(NodeScore(startNodeId, initialH))

        while (openSet.isNotEmpty()) {
            val current = openSet.poll() ?: break
            val currNodeId = current.nodeId

            if (currNodeId == endNodeId) {
                // Path found! Reconstruct.
                return reconstructPath(graph, cameFrom, endNodeId)
            }

            val currentG = gScore[currNodeId] ?: continue

            val edges = graph.adjacency[currNodeId] ?: emptyList()
            for (edge in edges) {
                val neighborId = edge.toNodeId
                val tentativeG = currentG + (edge.distanceKm * edge.costMultiplier)

                if (tentativeG < (gScore[neighborId] ?: Double.MAX_VALUE)) {
                    cameFrom[neighborId] = currNodeId
                    gScore[neighborId] = tentativeG

                    val neighborPoint = graph.nodes[neighborId]
                    val h = neighborPoint?.distanceTo(targetPoint) ?: 0.0
                    openSet.add(NodeScore(neighborId, tentativeG + h))
                }
            }
        }

        return null
    }

    private fun reconstructPath(
        graph: OsmRoadGraph,
        cameFrom: Map<Long, Long>,
        endNodeId: Long
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
            totalDistKm += points[i].distanceTo(points[i + 1])
        }

        return AStarResult(points, totalDistKm)
    }
}
