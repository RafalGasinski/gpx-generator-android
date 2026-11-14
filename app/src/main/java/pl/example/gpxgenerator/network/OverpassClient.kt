package pl.example.gpxgenerator.network

import android.content.Context
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
    val ways: List<OsmWay>,
    val fromCache: Boolean = false,
    val payloadSizeBytes: Long = 0L
)

object OverpassClient {

    private val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://z.overpass-api.de/api/interpreter"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    fun fetchRoadsInBoundingBox(
        context: Context,
        start: GeoPoint,
        end: GeoPoint,
        onProgress: (String) -> Unit = {}
    ): OverpassResponseData {
        val latSpan = abs(start.latitude - end.latitude)
        val lonSpan = abs(start.longitude - end.longitude)

        // Ciasny, zoptymalizowany korytarz wokół trasy (minimalny payload, max szybkość)
        val latMargin = max(0.03, latSpan * 0.12)
        val lonMargin = max(0.03, lonSpan * 0.12)

        val minLat = min(start.latitude, end.latitude) - latMargin
        val maxLat = max(start.latitude, end.latitude) + latMargin
        val minLon = min(start.longitude, end.longitude) - lonMargin
        val maxLon = max(start.longitude, end.longitude) + lonMargin

        // 1. Sprawdzenie lokalnej pamięci podręcznej (Cache)
        onProgress("⏳ Sprawdzanie lokalnej pamięci podręcznej (Cache)...")
        val cached = OverpassCacheManager.getCachedData(context, minLat, minLon, maxLat, maxLon)
        if (cached != null) {
            onProgress("⚡ Odczytano siatkę dróg z lokalnego Cache!")
            return OverpassResponseData(
                nodes = cached.data.nodes,
                ways = cached.data.ways,
                fromCache = true,
                payloadSizeBytes = 0L
            )
        }

        // 2. Lekkie, zoptymalizowane zapytanie Overpass QL (tylko drogi boczne/lokalne/powiatowe dla szybkiej odpowiedzi bez 502)
        val query = """
            [out:json][timeout:25];
            (
              way["highway"~"secondary|secondary_link|tertiary|tertiary_link|unclassified|residential|track|living_street"]($minLat,$minLon,$maxLat,$maxLon);
            );
            out body;
            >;
            out skel qt;
        """.trimIndent()

        val errors = mutableListOf<String>()

        ENDPOINTS.forEachIndexed { idx, endpoint ->
            val hostName = endpoint.removePrefix("https://").removeSuffix("/api/interpreter")
            onProgress("⏳ [Próba ${idx + 1}/${ENDPOINTS.size}] Łączenie z serwerem Overpass ($hostName)...")

            try {
                val formBody = FormBody.Builder()
                    .add("data", query)
                    .build()

                val request = Request.Builder()
                    .url(endpoint)
                    .post(formBody)
                    .header("User-Agent", "GPXGeneratorAndroid/1.0")
                    .header("Accept", "application/json")
                    .header("Connection", "close")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} z $hostName: ${response.message}")
                    }

                    onProgress("📥 Pobrano odpowiedź z $hostName. Parsowanie strumienia dróg...")

                    val charStream = response.body?.charStream()
                        ?: throw IOException("Pusta odpowiedź z Overpass API")

                    val dataFromNet = parseOverpassStream(charStream)

                    if (dataFromNet.ways.isNotEmpty()) {
                        OverpassCacheManager.saveToCache(context, dataFromNet, minLat, minLon, maxLat, maxLon)
                    }

                    return dataFromNet
                }
            } catch (e: Exception) {
                val errText = "$hostName: ${e.message}"
                errors.add(errText)
                if (idx < ENDPOINTS.size - 1) {
                    onProgress("⚠️ Błąd z $hostName (${e.message}). Przełączanie na kolejny serwer...")
                    runCatching { Thread.sleep(300) }
                }
            }
        }

        throw IOException("Błąd pobierania danych Overpass po ${errors.size} próbach. Szczegóły: ${errors.joinToString("; ")}")
    }

    private fun parseOverpassStream(charReader: Reader): OverpassResponseData {
        val tempNodes = HashMap<Long, GeoPoint>(20000)
        val ways = ArrayList<OsmWay>(3000)
        val referencedNodeIds = HashSet<Long>(20000)

        JsonReader(charReader).use { reader ->
            reader.isLenient = true
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

        return OverpassResponseData(activeNodes, ways, fromCache = false)
    }

    private fun abs(value: Double) = kotlin.math.abs(value)
}
