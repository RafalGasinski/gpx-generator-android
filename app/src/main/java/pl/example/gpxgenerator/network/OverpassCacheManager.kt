package pl.example.gpxgenerator.network

import android.content.Context
import android.util.JsonReader
import android.util.JsonWriter
import pl.example.gpxgenerator.model.GeoPoint
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.concurrent.TimeUnit

data class CachedRoadData(
    val data: OverpassResponseData,
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
    val timestamp: Long
)

object OverpassCacheManager {

    private const val CACHE_DIR_NAME = "overpass_cache"
    private const val MAX_CACHE_AGE_DAYS = 14L
    private const val MAX_CACHE_SIZE_BYTES = 50 * 1024 * 1024L // 50 MB

    fun clearAllCache(context: Context) {
        runCatching {
            File(context.cacheDir, CACHE_DIR_NAME).deleteRecursively()
        }
    }

    fun getCachedData(
        context: Context,
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double
    ): CachedRoadData? {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!cacheDir.exists()) return null

        cleanExpiredAndExcessCache(cacheDir)

        val files = cacheDir.listFiles { _, name -> name.endsWith(".json") } ?: return null
        val maxAgeMillis = TimeUnit.DAYS.toMillis(MAX_CACHE_AGE_DAYS)
        val now = System.currentTimeMillis()

        for (file in files) {
            val fileAge = now - file.lastModified()
            if (fileAge > maxAgeMillis) {
                file.delete()
                continue
            }

            runCatching {
                val cached = readCacheFile(file)
                if (cached != null) {
                    if (cached.data.ways.isEmpty() || cached.data.nodes.isEmpty()) {
                        file.delete()
                    } else if (cached.minLat <= minLat && cached.maxLat >= maxLat &&
                        cached.minLon <= minLon && cached.maxLon >= maxLon
                    ) {
                        return cached
                    }
                }
            }
        }
        return null
    }

    fun saveToCache(
        context: Context,
        data: OverpassResponseData,
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double
    ) {
        if (data.ways.isEmpty() || data.nodes.isEmpty()) return

        runCatching {
            val cacheDir = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
            cleanExpiredAndExcessCache(cacheDir)

            val fileName = "bbox_%.2f_%.2f_%.2f_%.2f.json".format(minLat, minLon, maxLat, maxLon)
                .replace(",", ".")
            val file = File(cacheDir, fileName)

            writeCacheFile(file, data, minLat, minLon, maxLat, maxLon)
        }
    }

    private fun readCacheFile(file: File): CachedRoadData? {
        FileReader(file).use { fileReader ->
            JsonReader(fileReader).use { reader ->
                var minLat = 0.0
                var minLon = 0.0
                var maxLat = 0.0
                var maxLon = 0.0
                var timestamp = file.lastModified()
                var parsedNodes: Map<Long, GeoPoint>? = null
                var parsedWays: List<OsmWay>? = null

                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "minLat" -> minLat = reader.nextDouble()
                        "minLon" -> minLon = reader.nextDouble()
                        "maxLat" -> maxLat = reader.nextDouble()
                        "maxLon" -> maxLon = reader.nextDouble()
                        "timestamp" -> timestamp = reader.nextLong()
                        "nodes" -> {
                            val tempNodes = HashMap<Long, GeoPoint>()
                            reader.beginArray()
                            while (reader.hasNext()) {
                                reader.beginObject()
                                var id = 0L
                                var lat = 0.0
                                var lon = 0.0
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "id" -> id = reader.nextLong()
                                        "lat" -> lat = reader.nextDouble()
                                        "lon" -> lon = reader.nextDouble()
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                tempNodes[id] = GeoPoint(lat, lon)
                            }
                            reader.endArray()
                            parsedNodes = tempNodes
                        }
                        "ways" -> {
                            val waysList = ArrayList<OsmWay>()
                            reader.beginArray()
                            while (reader.hasNext()) {
                                reader.beginObject()
                                var id = 0L
                                var highway = "unclassified"
                                var surface: String? = null
                                var isOneway = false
                                val nodeIds = ArrayList<Long>()

                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "id" -> id = reader.nextLong()
                                        "hw" -> highway = reader.nextString()
                                        "surf" -> {
                                            if (reader.peek() == android.util.JsonToken.NULL) {
                                                reader.nextNull()
                                            } else {
                                                surface = reader.nextString()
                                            }
                                        }
                                        "ow" -> isOneway = reader.nextBoolean()
                                        "nodes" -> {
                                            reader.beginArray()
                                            while (reader.hasNext()) {
                                                nodeIds.add(reader.nextLong())
                                            }
                                            reader.endArray()
                                        }
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                waysList.add(OsmWay(id, nodeIds, highway, surface, isOneway))
                            }
                            reader.endArray()
                            parsedWays = waysList
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()

                return if (parsedNodes != null && parsedWays != null) {
                    val resData = OverpassResponseData(parsedNodes, parsedWays, fromCache = true)
                    CachedRoadData(resData, minLat, minLon, maxLat, maxLon, timestamp)
                } else null
            }
        }
    }

    private fun writeCacheFile(
        file: File,
        data: OverpassResponseData,
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double
    ) {
        FileWriter(file).use { fileWriter ->
            JsonWriter(fileWriter).use { writer ->
                writer.beginObject()
                writer.name("minLat").value(minLat)
                writer.name("minLon").value(minLon)
                writer.name("maxLat").value(maxLat)
                writer.name("maxLon").value(maxLon)
                writer.name("timestamp").value(System.currentTimeMillis())

                writer.name("nodes")
                writer.beginArray()
                for ((id, pt) in data.nodes) {
                    writer.beginObject()
                    writer.name("id").value(id)
                    writer.name("lat").value(pt.latitude)
                    writer.name("lon").value(pt.longitude)
                    writer.endObject()
                }
                writer.endArray()

                writer.name("ways")
                writer.beginArray()
                for (way in data.ways) {
                    writer.beginObject()
                    writer.name("id").value(way.id)
                    writer.name("hw").value(way.highwayType)
                    if (way.surface != null) {
                        writer.name("surf").value(way.surface)
                    } else {
                        writer.name("surf").nullValue()
                    }
                    writer.name("ow").value(way.isOneway)
                    writer.name("nodes")
                    writer.beginArray()
                    for (nId in way.nodeIds) {
                        writer.value(nId)
                    }
                    writer.endArray()
                    writer.endObject()
                }
                writer.endArray()

                writer.endObject()
            }
        }
    }

    private fun cleanExpiredAndExcessCache(cacheDir: File) {
        val files = cacheDir.listFiles { _, name -> name.endsWith(".json") } ?: return
        val now = System.currentTimeMillis()
        val maxAgeMillis = TimeUnit.DAYS.toMillis(MAX_CACHE_AGE_DAYS)

        var totalSize = 0L
        val sortedFiles = files.sortedBy { it.lastModified() }

        for (file in sortedFiles) {
            val fileAge = now - file.lastModified()
            if (fileAge > maxAgeMillis) {
                file.delete()
            } else {
                totalSize += file.length()
            }
        }

        if (totalSize > MAX_CACHE_SIZE_BYTES) {
            for (file in sortedFiles) {
                if (file.exists()) {
                    totalSize -= file.length()
                    file.delete()
                    if (totalSize <= MAX_CACHE_SIZE_BYTES * 0.7) break
                }
            }
        }
    }
}
