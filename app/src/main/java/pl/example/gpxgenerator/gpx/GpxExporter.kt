package pl.example.gpxgenerator.gpx

import pl.example.gpxgenerator.generator.RouteCandidate

object GpxExporter {
    // Eksportuje punkty jako GPX waypoints, nie udaje śladu po drogach.
    fun exportWaypoints(candidate: RouteCandidate, name: String): String {
        val allPoints = listOf(candidate.start) + candidate.controlPoints + candidate.end
        val escaped = xmlEscape(name)
        val waypoints = allPoints.joinToString("\n") { p ->
            "  <wpt lat=\"${p.latitude}\" lon=\"${p.longitude}\"><name>Point</name></wpt>"
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<gpx version=\"1.1\" creator=\"GPX Generator Android\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n" +
            "  <metadata><name>$escaped</name></metadata>\n$waypoints\n</gpx>"
    }
    private fun xmlEscape(v: String) = v.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
