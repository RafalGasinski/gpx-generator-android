package pl.example.gpxgenerator.model

data class RoutePreferences(
    val avoidHighways: Boolean = true,
    val allowGravel: Boolean = false,
    val profile: String = "motorcycle"
)
