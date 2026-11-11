package pl.example.gpxgenerator

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import pl.example.gpxgenerator.model.GeoPoint
import pl.example.gpxgenerator.model.RoutePreferences
import pl.example.gpxgenerator.routing.OsmAndHelper
import pl.example.gpxgenerator.routing.OsmAndStatus
import pl.example.gpxgenerator.routing.RouteHandoffResult
import pl.example.gpxgenerator.routing.RouteRequest

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

    var startText by remember { mutableStateOf("49.9961, 20.0358") }
    var endText by remember { mutableStateOf("49.6892, 19.2058") }
    var allowGravel by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var currentCandidate by remember { mutableStateOf<RouteCandidate?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val osmandStatus = remember { mutableStateOf(OsmAndHelper.checkOsmAndStatus(context)) }

    LaunchedEffect(Unit) {
        osmandStatus.value = OsmAndHelper.checkOsmAndStatus(context)
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
                    text = "Generuj rzeczywiste trasy po drogach utwardzonych z Overpass API (z ominięciem autostrad A, ekspresówek S i dróg krajowych DK).",
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
                }) {
                    Text("Odśwież")
                }
            }
        }

        // Sekcja Współrzędnych
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Punkty Trasy",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = { Text("Start: lat, lon") },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = { Text("Meta: lat, lon") },
                    singleLine = true,
                    enabled = !isLoading,
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
                                if (!isLoading) {
                                    startText = preset.start
                                    endText = preset.end
                                }
                            },
                            label = { Text(preset.title) },
                            enabled = !isLoading
                        )
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
                        onCheckedChange = { allowGravel = it },
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

        // Przycisk Generowania i Stan Ładowania
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
                            text = "Pobieranie siatki dróg z Overpass API...",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Budowanie grafu i wyliczanie trasy A* po drogach bocznych.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else {
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        statusMessage = null
                        runCatching {
                            val s = parseGeoPoint(startText)
                            val e = parseGeoPoint(endText)
                            val prefs = RoutePreferences(avoidHighways = true, allowGravel = allowGravel, profile = "motorcycle")

                            val candidate = withContext(Dispatchers.IO) {
                                RouteCandidateGenerator.generateRealRoadRoute(s, e, prefs)
                            }

                            currentCandidate = candidate
                            isError = false
                            statusMessage = "Pomyślnie wyznaczono trasę po realnych drogach! Szacowany dystans: %.1f km, punkty śladu GPX: %d.".format(
                                candidate.estimatedDistanceKm,
                                candidate.trackPointCount
                            )
                        }.onFailure { err ->
                            isError = true
                            statusMessage = "Błąd trasowania: ${err.message}"
                        }
                        isLoading = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Wygeneruj trasę po drogach", fontSize = 16.sp, modifier = Modifier.padding(vertical = 4.dp))
            }
        }

        // Podsumowanie wygenerowanej trasy i Akcje
        currentCandidate?.let { candidate ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val (_, uri) = GpxExporter.saveGpxFile(context, candidate)
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
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Otwórz w OsmAnd")
                        }

                        OutlinedButton(
                            onClick = {
                                val (_, uri) = GpxExporter.saveGpxFile(context, candidate)
                                GpxExporter.shareGpxFile(context, uri)
                                Toast.makeText(context, "Zapisano plik GPX do udostępnienia.", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Zapisz / Udostępnij GPX")
                        }
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
    val parts = value.split(",").map { it.trim().toDoubleOrNull() }
    require(parts.size == 2 && parts[0] != null && parts[1] != null) {
        "Wprowadź poprawne współrzędne w formacie lat, lon (np. 49.9961, 20.0358)."
    }
    val lat = parts[0]!!
    val lon = parts[1]!!
    require(lat in -90.0..90.0 && lon in -180.0..180.0) {
        "Współrzędne poza dozwolonym zakresem geograficznym."
    }
    return GeoPoint(lat, lon)
}
