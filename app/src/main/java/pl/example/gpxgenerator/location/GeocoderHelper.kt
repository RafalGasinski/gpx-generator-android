package pl.example.gpxgenerator.location

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import pl.example.gpxgenerator.model.GeoPoint
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

data class SearchResultAddress(
    val displayName: String,
    val point: GeoPoint
)

object GeocoderHelper {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun findAddress(context: Context, query: String): List<SearchResultAddress> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        // 1. Natywny Android Geocoder
        runCatching {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocationName(trimmed, 5)
            if (!addresses.isNullOrEmpty()) {
                return@withContext addresses.mapNotNull { addr ->
                    val line = addr.getAddressLine(0) ?: addr.locality ?: addr.featureName
                    if (line != null) {
                        SearchResultAddress(line, GeoPoint(addr.latitude, addr.longitude))
                    } else null
                }
            }
        }

        // 2. Fallback: OpenStreetMap Nominatim API
        runCatching {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=5"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "GPXGeneratorAndroid/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string()
                    if (!jsonStr.isNullOrEmpty()) {
                        val array = JSONArray(jsonStr)
                        val results = mutableListOf<SearchResultAddress>()
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val name = obj.optString("display_name")
                            val lat = obj.optDouble("lat")
                            val lon = obj.optDouble("lon")
                            if (!name.isNullOrEmpty() && !lat.isNaN() && !lon.isNaN()) {
                                results.add(SearchResultAddress(name, GeoPoint(lat, lon)))
                            }
                        }
                        return@withContext results
                    }
                }
            }
        }

        emptyList()
    }
}
