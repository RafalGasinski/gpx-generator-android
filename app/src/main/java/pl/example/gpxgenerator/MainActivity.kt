package pl.example.gpxgenerator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.example.gpxgenerator.generator.RouteCandidateGenerator
import pl.example.gpxgenerator.generator.RouteSettings
import pl.example.gpxgenerator.gpx.GpxExporter
import pl.example.gpxgenerator.model.GeoPoint

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { GeneratorScreen() } }
    }
}

@Composable
private fun GeneratorScreen() {
    var start by remember { mutableStateOf("49.9961, 20.0358") }
    var end by remember { mutableStateOf("49.7467, 19.2972") }
    var allowUnpaved by remember { mutableStateOf(false) }
    var shortest by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("To prototyp interfejsu. AIDL do OsmAnd nie jest jeszcze podłączone.") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("GPX Generator", style = MaterialTheme.typography.headlineMedium)
        Text("Start i meta oraz preferencje. Następny etap: wybór punktów pośrednich i integracja AIDL.")
        OutlinedTextField(start, { start = it }, label = { Text("Start: lat, lon") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(end, { end = it }, label = { Text("Meta: lat, lon") }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(allowUnpaved, { allowUnpaved = it })
            Text("Dopuszczaj drogi nieutwardzone")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(shortest, { shortest = it })
            Text("Preferuj krótszą trasę")
        }
        Button(onClick = {
            runCatching {
                val s = parsePoint(start)
                val e = parsePoint(end)
                val candidate = RouteCandidateGenerator.generate(s, e, RouteSettings(allowUnpaved, shortest))
                val gpx = GpxExporter.exportWaypoints(candidate, "GPX Generator candidate")
                status = "Przygotowano szkic GPX (${gpx.length} znaków). Zapis pliku i przekazanie do OsmAnd są kolejnym krokiem."
            }.onFailure { status = "Błąd: ${it.message}" }
        }, modifier = Modifier.fillMaxWidth()) { Text("Przygotuj GPX") }
        Text(status)
    }
}

private fun parsePoint(value: String): GeoPoint {
    val parts = value.split(",").map { it.trim().toDouble() }
    require(parts.size == 2) { "Podaj współrzędne w formacie lat, lon." }
    require(parts[0] in -90.0..90.0 && parts[1] in -180.0..180.0) { "Współrzędne poza zakresem." }
    return GeoPoint(parts[0], parts[1])
}
