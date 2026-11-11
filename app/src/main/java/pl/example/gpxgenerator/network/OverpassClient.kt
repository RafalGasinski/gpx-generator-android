package pl.example.gpxgenerator.network

import android.util.JsonReader
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import pl.example.gpxgenerator.model.GeoPoint
import java.io.IOException
import java.io.Reader
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

data class OsmWay(
    val id: Long,
    val nodeIds: List<Long>,
    val highwayType: String,
    val surface: String?,
    val isOneway: Boolean
)

data class OverpassResponseData(
    val nodes: Map<Long, GeoPoint>,
    val ways: List<OsmWay>
)

object OverpassClient {

    private val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://z.overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    fun fetchRoadsInBoundingBox(start: GeoPoint, end: GeoPoint): OverpassResponseData {
        val latSpan = abs(start.latitude - end.latitude)
        val lonSpan = abs(start.longitude - end.longitude)

        val latMargin = min(0.06, max(0.025, latSpan * 0.10))
        val lonMargin = min(0.06, max(0.025, lonSpan * 0.10))

        val minLat = min(start.latitude, end.latitude) - latMargin
        val maxLat = max(start.latitude, end.latitude) + latMargin
        val minLon = min(start.longitude, end.longitude) - lonMargin
        val maxLon = max(start.longitude, end.longitude) + lonMargin

        val query = """
            [out:json][timeout:45];
            (
              way["highway"~"secondary|secondary_link|tertiary|tertiary_link|unclassified|residential|track|living_street"]($minLat,$minLon,$maxLat,$maxLon);
            );
            out body;
            >;
            out skel qt;
        """.trimIndent()

        val errors = mutableListOf<String>()

        for (endpoint in ENDPOINTS) {
            try {
                val formBody = FormBody.Builder()
                    .add("data", query)
                    .build()

                val request = Request.Builder()
                    .url(endpoint)
                    .post(formBody)
                    .header("User-Agent", "GPXGeneratorAndroid/1.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} z $endpoint: ${response.message}")
                    }
                    val bodyReader = response.body?.charStream()
                        ?: throw IOException("Pusta odpowiedź z Overpass API")

                    return parseOverpassStream(bodyReader)
                }
            } catch (e: Exception) {
                errors.add("$endpoint: ${e.message}")
            }
        }

        throw IOException("Błąd pobierania danych Overpass (${errors.size} prób). Błędy: ${errors.joinToString("; ")}")
    }

    private fun parseOverpassStream(charReader: Reader): OverpassResponseData {
        val tempNodes = HashMap<Long, GeoPoint>(20000)
        val ways = ArrayList<OsmWay>(3000)
        val referencedNodeIds = HashSet<Long>(20000)

        JsonReader(charReader).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                val topName = reader.nextName()
                if (topName == "elements") {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        reader.beginObject()
                        var type = ""
                        var id = 0L
                        var lat = 0.0
                        var lon = 0.0
                        var highway: String? = null
                        var surface: String? = null
                        var isOneway = false
                        var nodeIds: ArrayList<Long>? = null

                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "type" -> type = reader.nextString()
                                "id" -> id = reader.nextLong()
                                "lat" -> lat = reader.nextDouble()
                                "lon" -> lon = reader.nextDouble()
                                "nodes" -> {
                                    nodeIds = ArrayList()
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        val nId = reader.nextLong()
                                        nodeIds.add(nId)
                                        referencedNodeIds.add(nId)
                                    }
                                    reader.endArray()
                                }
                                "tags" -> {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        when (reader.nextName()) {
                                            "highway" -> highway = reader.nextString()
                                            "surface" -> surface = reader.nextString()
                                            "oneway" -> {
                                                val ow = reader.nextString()
                                                isOneway = ow == "yes" || ow == "1" || ow == "true"
                                            }
                                            else -> reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                }
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()

                        if (type == "node") {
                            tempNodes[id] = GeoPoint(lat, lon)
                        } else if (type == "way" && highway != null && nodeIds != null) {
                            ways.add(OsmWay(id, nodeIds, highway, surface, isOneway))
                        }
                    }
                    reader.endArray()
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
        }

        val activeNodes = HashMap<Long, GeoPoint>(referencedNodeIds.size)
        for (nodeId in referencedNodeIds) {
            val pt = tempNodes[nodeId]
            if (pt != null) {
                activeNodes[nodeId] = pt
            }
        }

        return OverpassResponseData(activeNodes, ways)
    }

    private fun abs(value: Double) = kotlin.math.abs(value)
}
