package pl.example.gpxgenerator

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.example.gpxgenerator.generator.RouteCandidate
import pl.example.gpxgenerator.generator.RouteCandidateGenerator
import pl.example.gpxgenerator.gpx.GpxExporter
import pl.example.gpxgenerator.location.GeocoderHelper
import pl.example.gpxgenerator.location.SearchResultAddress
import pl.example.gpxgenerator.model.GeoPoint
import pl.example.gpxgenerator.model.RoutePreferences
import pl.example.gpxgenerator.routing.NavAppDetector
import pl.example.gpxgenerator.routing.OsmAndHelper
import pl.example.gpxgenerator.routing.OsmAndStatus
import pl.example.gpxgenerator.routing.RouteHandoffResult
import pl.example.gpxgenerator.routing.RouteRequest
import pl.example.gpxgenerator.routing.WebNavHelper
import pl.example.gpxgenerator.ui.MapSelectionMode
import pl.example.gpxgenerator.ui.OsmMapView
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OsmAndHelper.bindAidlService(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GeneratorScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        OsmAndHelper.unbindAidlService(this)
    }
}

private data class RoutePreset(val title: String, val start: String, val end: String)

private val PRESETS = listOf(
    RoutePreset("Beskidy (Kraków -> Żywiec)", "49.9961, 20.0358", "49.6892, 19.2058"),
    RoutePreset("Podhale (Nowy Sącz -> Zakopane)", "49.6218, 20.6970", "49.2992, 19.9496"),
    RoutePreset("Bieszczady (Sanok -> Ustrzyki Górne)", "49.5609, 22.2063", "49.1064, 22.6481")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeneratorScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var startQuery by remember { mutableStateOf("Kraków") }
    var endQuery by remember { mutableStateOf("Żywiec") }

    var startPoint by remember { mutableStateOf<GeoPoint?>(GeoPoint(49.9961, 20.0358)) }
    var endPoint by remember { mutableStateOf<GeoPoint?>(GeoPoint(49.6892, 19.2058)) }

    var startCoordText by remember { mutableStateOf("49.9961, 20.0358") }
    var endCoordText by remember { mutableStateOf("49.6892, 19.2058") }

    var showAdvancedCoords by remember { mutableStateOf(false) }

    var mapSelectionMode by remember { mutableStateOf(MapSelectionMode.SET_START) }
    var allowGravel by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var liveProgressText by remember { mutableStateOf("Przygotowywanie...") }

    var isSearchingStart by remember { mutableStateOf(false) }
    var isSearchingEnd by remember { mutableStateOf(false) }

    var startSearchResults by remember { mutableStateOf<List<SearchResultAddress>>(emptyList()) }
    var endSearchResults by remember { mutableStateOf<List<SearchResultAddress>>(emptyList()) }

    var currentCandidate by remember { mutableStateOf<RouteCandidate?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val osmandStatus = remember { mutableStateOf(OsmAndHelper.checkOsmAndStatus(context)) }
    val installedNavApps = remember { mutableStateOf(NavAppDetector.getInstalledNavApps(context)) }

    LaunchedEffect(Unit) {
        osmandStatus.value = OsmAndHelper.checkOsmAndStatus(context)
        installedNavApps.value = NavAppDetector.getInstalledNavApps(context)
    }

    fun resetCandidateState() {
        currentCandidate = null
        statusMessage = null
        isError = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Nagłówek aplikacji
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "GPX Generator Motocyklowy",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Przesuwaj pinezki palcem na mapie lub wpisuj miasta, aby wyznaczyć trasy po drogach utwardzonych (bez A, S i DK).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Status OsmAnd
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (osmandStatus.value) {
                    is OsmAndStatus.Installed -> MaterialTheme.colorScheme.secondaryContainer
                    OsmAndStatus.NotInstalled -> MaterialTheme.colorScheme.errorContainer
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Status OsmAnd",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    when (val s = osmandStatus.value) {
                        is OsmAndStatus.Installed -> {
                            Text(
                                text = "Zainstalowano: ${s.packageName} ${if (s.isAidlConnected) "(AIDL Połączono)" else "(Gotowy Intent)"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OsmAndStatus.NotInstalled -> {
                            Text(
                                text = "Brak zainstalowanej aplikacji OsmAnd! Zainstaluj OsmAnd ze Sklepu Google Play.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                TextButton(onClick = {
                    OsmAndHelper.bindAidlService(context)
                    osmandStatus.value = OsmAndHelper.checkOsmAndStatus(context)
                    installedNavApps.value = NavAppDetector.getInstalledNavApps(context)
                }) {
                    Text("Odśwież")
                }
            }
        }

        // Sekcja Wyszukiwarki Adresów i Miejsc
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Wyszukiwarka Adresów i Miejsc",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = startQuery,
                        onValueChange = {
                            startQuery = it
                            startPoint = null
                            resetCandidateState()
                        },
                        label = { Text("Punkt Start (Adres / Miasto)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                isSearchingStart = true
                                val res = GeocoderHelper.findAddress(context, startQuery)
                                startSearchResults = res
                                isSearchingStart = false
                                if (res.isNotEmpty()) {
                                    val top = res.first()
                                    startPoint = top.point
                                    startCoordText = String.format(Locale.US, "%.4f, %.4f", top.point.latitude, top.point.longitude)
                                    resetCandidateState()
                                } else {
                                    Toast.makeText(context, "Nie znaleziono adresu dla: $startQuery", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isLoading && !isSearchingStart
                    ) {
                        Text(if (isSearchingStart) "..." else "Szukaj")
                    }
                }

                if (startSearchResults.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Wybierz lokalizację Startu:", style = MaterialTheme.typography.labelSmall)
                            startSearchResults.forEach { item ->
                                Text(
                                    text = "• ${item.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            startPoint = item.point
                                            startCoordText = String.format(Locale.US, "%.4f, %.4f", item.point.latitude, item.point.longitude)
                                            startQuery = item.displayName.split(",").firstOrNull() ?: item.displayName
                                            startSearchResults = emptyList()
                                            resetCandidateState()
                                        }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = endQuery,
                        onValueChange = {
                            endQuery = it
                            endPoint = null
                            resetCandidateState()
                        },
                        label = { Text("Punkt Meta (Adres / Miasto)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                isSearchingEnd = true
                                val res = GeocoderHelper.findAddress(context, endQuery)
                                endSearchResults = res
                                isSearchingEnd = false
                                if (res.isNotEmpty()) {
                                    val top = res.first()
                                    endPoint = top.point
                                    endCoordText = String.format(Locale.US, "%.4f, %.4f", top.point.latitude, top.point.longitude)
                                    resetCandidateState()
                                } else {
                                    Toast.makeText(context, "Nie znaleziono adresu dla: $endQuery", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isLoading && !isSearchingEnd
                    ) {
                        Text(if (isSearchingEnd) "..." else "Szukaj")
                    }
                }

                if (endSearchResults.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Wybierz lokalizację Mety:", style = MaterialTheme.typography.labelSmall)
                            endSearchResults.forEach { item ->
                                Text(
                                    text = "• ${item.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            endPoint = item.point
                                            endCoordText = String.format(Locale.US, "%.4f, %.4f", item.point.latitude, item.point.longitude)
                                            endQuery = item.displayName.split(",").firstOrNull() ?: item.displayName
                                            endSearchResults = emptyList()
                                            resetCandidateState()
                                        }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sekcja Interaktywnej Mapy OSM
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Interaktywna Mapa OSM",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = mapSelectionMode == MapSelectionMode.SET_START,
                            onClick = { mapSelectionMode = MapSelectionMode.SET_START },
                            label = { Text("🟢 Klikaj: Start", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = mapSelectionMode == MapSelectionMode.SET_END,
                            onClick = { mapSelectionMode = MapSelectionMode.SET_END },
                            label = { Text("🔴 Klikaj: Meta", fontSize = 11.sp) }
                        )
                    }
                }

                OsmMapView(
                    startPoint = startPoint,
                    endPoint = endPoint,
                    routeCandidate = currentCandidate,
                    selectionMode = mapSelectionMode,
                    onPointSelected = { clickedPt ->
                        resetCandidateState()
                        if (mapSelectionMode == MapSelectionMode.SET_START) {
                            startPoint = clickedPt
                            startCoordText = String.format(Locale.US, "%.4f, %.4f", clickedPt.latitude, clickedPt.longitude)
                            startQuery = "Punkt z mapy"
                        } else if (mapSelectionMode == MapSelectionMode.SET_END) {
                            endPoint = clickedPt
                            endCoordText = String.format(Locale.US, "%.4f, %.4f", clickedPt.latitude, clickedPt.longitude)
                            endQuery = "Punkt z mapy"
                        }
                    },
                    onStartPointSet = { draggedPt ->
                        resetCandidateState()
                        startPoint = draggedPt
                        startCoordText = String.format(Locale.US, "%.4f, %.4f", draggedPt.latitude, draggedPt.longitude)
                        startQuery = "Przesunięty Start"
                    },
                    onEndPointSet = { draggedPt ->
                        resetCandidateState()
                        endPoint = draggedPt
                        endCoordText = String.format(Locale.US, "%.4f, %.4f", draggedPt.latitude, draggedPt.longitude)
                        endQuery = "Przesunięta Meta"
                    }
                )

                Text(
                    text = "💡 Wskazówka: Przesuwaj zieloną (Start) i czerwoną (Meta) pinezkę bezpośrednio palcem po mapie!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Sekcja Zaawansowana: Ręczne Współrzędne
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvancedCoords = !showAdvancedCoords },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚙️ Zaawansowane: Ręczne współrzędne (lat, lon)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(if (showAdvancedCoords) "▲ Zwiń" else "▼ Rozwiń", style = MaterialTheme.typography.labelMedium)
                }

                AnimatedVisibility(visible = showAdvancedCoords) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = startCoordText,
                            onValueChange = {
                                startCoordText = it
                                resetCandidateState()
                                runCatching {
                                    startPoint = parseGeoPoint(it)
                                }
                            },
                            label = { Text("Start: lat, lon") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = endCoordText,
                            onValueChange = {
                                endCoordText = it
                                resetCandidateState()
                                runCatching {
                                    endPoint = parseGeoPoint(it)
                                }
                            },
                            label = { Text("Meta: lat, lon") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "Szybki wybór przykładowych tras:",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PRESETS.forEach { preset ->
                                SuggestionChip(
                                    onClick = {
                                        resetCandidateState()
                                        startCoordText = preset.start
                                        endCoordText = preset.end
                                        startPoint = parseGeoPoint(preset.start)
                                        endPoint = parseGeoPoint(preset.end)
                                        startQuery = preset.title.split("(").first().trim()
                                        endQuery = preset.title.split("->").last().replace(")", "").trim()
                                    },
                                    label = { Text(preset.title) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sekcja Preferencji Drogowych
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Ustawienia i Preferencje Drogowe",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Profil nawigacji:")
                    AssistChip(
                        onClick = {},
                        label = { Text("Motocykl (motorcycle)") }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = allowGravel,
                        onCheckedChange = {
                            allowGravel = it
                            resetCandidateState()
                        },
                        enabled = !isLoading
                    )
                    Column {
                        Text(
                            text = "Dopuszczaj drogi szutrowe / nieutwardzone",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Domyślnie preferowane są boczne drogi utwardzone (max szuter).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✓ Domyślnie omijane są: Autostrady (A), Drogi Ekspresowe (S) oraz Drogi Krajowe (DK).",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        // Przycisk Generowania i Stan Ładowania na Żywo
        if (isLoading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Column {
                        Text(
                            text = "Wyznaczanie trasy na żywo:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = liveProgressText,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            isError = false
                            statusMessage = null
                            currentCandidate = null
                            liveProgressText = "Rozpoczynanie trasowania..."

                            runCatching {
                                // 1. Rozstrzyganie punktu Start
                                var activeStart = startPoint
                                if (activeStart == null) {
                                    if (startQuery.isNotBlank() && !startQuery.startsWith("Punkt z mapy")) {
                                        activeStart = GeocoderHelper.findAddress(context, startQuery).firstOrNull()?.point
                                    }
                                }
                                val finalStart = activeStart ?: parseGeoPoint(startCoordText)
                                startPoint = finalStart

                                // 2. Rozstrzyganie punktu Meta
                                var activeEnd = endPoint
                                if (activeEnd == null) {
                                    if (endQuery.isNotBlank() && !endQuery.startsWith("Punkt z mapy")) {
                                        activeEnd = GeocoderHelper.findAddress(context, endQuery).firstOrNull()?.point
                                    }
                                }
                                val finalEnd = activeEnd ?: parseGeoPoint(endCoordText)
                                endPoint = finalEnd

                                val prefs = RoutePreferences(avoidHighways = true, allowGravel = allowGravel, profile = "motorcycle")

                                val candidate = withContext(Dispatchers.IO) {
                                    RouteCandidateGenerator.generateRealRoadRoute(context, finalStart, finalEnd, prefs) { progressMsg ->
                                        scope.launch(Dispatchers.Main) {
                                            liveProgressText = progressMsg
                                        }
                                    }
                                }

                                currentCandidate = candidate
                                isError = false
                                val sourceTag = if (candidate.fromCache) "⚡ Cache" else "🌐 Network"
                                val totalSec = candidate.executionTimeMs / 1000.0
                                statusMessage = "Pomyślnie wyznaczono trasę! [$sourceTag w %.2f s]. Dystans: %.1f km.".format(
                                    totalSec,
                                    candidate.estimatedDistanceKm
                                )
                            }.onFailure { err ->
                                isError = true
                                currentCandidate = null
                                statusMessage = "Błąd trasowania [${err.javaClass.simpleName}]: ${err.message}"
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Wygeneruj trasę po drogach", fontSize = 16.sp, modifier = Modifier.padding(vertical = 4.dp))
                }

                OutlinedButton(
                    onClick = {
                        startQuery = "Kraków"
                        endQuery = "Żywiec"
                        startPoint = GeoPoint(49.9961, 20.0358)
                        endPoint = GeoPoint(49.6892, 19.2058)
                        startCoordText = "49.9961, 20.0358"
                        endCoordText = "49.6892, 19.2058"
                        allowGravel = false
                        mapSelectionMode = MapSelectionMode.SET_START
                        pl.example.gpxgenerator.network.OverpassCacheManager.clearAllCache(context)
                        resetCandidateState()
                        Toast.makeText(context, "Zresetowano punkty, trasę i wyczyszczono pamięć podręczną.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🧹 Wyczyść / Resetuj trasę")
                }
            }
        }

        // Podsumowanie wygenerowanej trasy i Diagnostyka Zasobów
        currentCandidate?.let { candidate ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Podsumowanie Rzeczywistej Trasy",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "• Szacowana długość trasy: %.1f km".format(candidate.estimatedDistanceKm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "• Geometria drogi GPX: ${candidate.trackPointCount} punktów śladu (<trkpt>)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    Text(
                        text = "Diagnostyka Zasobów i Wydajności:",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )

                    val payloadKb = candidate.payloadSizeBytes / 1024.0
                    val payloadMb = payloadKb / 1024.0
                    val payloadText = if (candidate.fromCache) {
                        "0 KB (Odczytano z pamięci podręcznej)"
                    } else if (payloadMb >= 1.0) {
                        "%.2f MB (%d B)".format(payloadMb, candidate.payloadSizeBytes)
                    } else {
                        "%.1f KB (%d B)".format(payloadKb, candidate.payloadSizeBytes)
                    }

                    val totalSec = candidate.executionTimeMs / 1000.0
                    val fetchSec = candidate.fetchTimeMs / 1000.0
                    val routerSec = candidate.routerTimeMs / 1000.0

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "🌐 Dane sieciowe (Payload): $payloadText",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "🧠 Alokacja sterty RAM: %.1f MB".format(candidate.ramUsedMb),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "⏱️ Czas łączny: %.2f s (Sieć/Cache: %.2f s | A* Router: %.3f s)".format(
                                    totalSec, fetchSec, routerSec
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // SEKCJA 1: Otwórz w Aplikacji (Natywne nawigacje w telefonie)
                    val appsList = installedNavApps.value
                    if (appsList.isNotEmpty()) {
                        Text(
                            text = "📱 Otwórz w aplikacji:",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )

                        val primaryApp = appsList.first()
                        val otherApps = appsList.drop(1)

                        Button(
                            onClick = {
                                val (_, uri) = GpxExporter.saveGpxFile(context, candidate)
                                if (primaryApp.isOsmAnd) {
                                    scope.launch {
                                        val req = RouteRequest(
                                            start = candidate.start,
                                            end = candidate.end,
                                            intermediates = emptyList(),
                                            profile = "motorcycle"
                                        )
                                        val res = OsmAndHelper.handoffRoute(context, req, uri)
                                        when (res) {
                                            is RouteHandoffResult.Success -> {
                                                isError = false
                                                statusMessage = "${res.message} [Metoda: ${res.method}]"
                                            }
                                            is RouteHandoffResult.Failed -> {
                                                isError = true
                                                statusMessage = res.reason
                                            }
                                        }
                                    }
                                } else {
                                    val msg = NavAppDetector.openInApp(context, primaryApp, uri)
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Otwórz w ${primaryApp.displayName}")
                        }

                        if (otherApps.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                otherApps.forEach { app ->
                                    OutlinedButton(
                                        onClick = {
                                            val (_, uri) = GpxExporter.saveGpxFile(context, candidate)
                                            val msg = NavAppDetector.openInApp(context, app, uri)
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    ) {
                                        Text(app.displayName)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // SEKCJA 2: Otwórz w Przeglądarce
                    Text(
                        text = "🌐 Otwórz w przeglądarce:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )

                    val webTools = WebNavHelper.WEB_TOOLS
                    val primaryWebTool = webTools.first { it.isPrimary }
                    val otherWebTools = webTools.filter { !it.isPrimary }

                    Button(
                        onClick = {
                            val msg = WebNavHelper.openInWebTool(context, primaryWebTool, candidate)
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Otwórz w ${primaryWebTool.displayName}")
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        otherWebTools.forEach { tool ->
                            OutlinedButton(
                                onClick = {
                                    val msg = WebNavHelper.openInWebTool(context, tool, candidate)
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            ) {
                                Text(tool.displayName)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Uniwersalny Przycisk Udostępniania GPX
                    OutlinedButton(
                        onClick = {
                            val (_, uri) = GpxExporter.saveGpxFile(context, candidate)
                            GpxExporter.shareGpxFile(context, uri)
                            Toast.makeText(context, "Zapisano plik GPX do udostępnienia.", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📤 Udostępnij plik GPX")
                    }
                }
            }
        }

        // Komunikat statusu / błędu
        statusMessage?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

private fun parseGeoPoint(value: String): GeoPoint {
    val numbers = Regex("[-+]?\\d+(?:[.,]\\d+)?").findAll(value).map { it.value.replace(",", ".").toDouble() }.toList()
    require(numbers.size >= 2) {
        "Wprowadź poprawne współrzędne w formacie lat, lon (np. 49.9961, 20.0358)."
    }
    val lat = numbers[0]
    val lon = numbers[1]
    require(lat in -90.0..90.0 && lon in -180.0..180.0) {
        "Współrzędne poza dozwolonym zakresem geograficznym."
    }
    return GeoPoint(lat, lon)
}
