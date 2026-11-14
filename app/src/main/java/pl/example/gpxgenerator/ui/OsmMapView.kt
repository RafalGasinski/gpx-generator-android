package pl.example.gpxgenerator.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import pl.example.gpxgenerator.generator.RouteCandidate
import pl.example.gpxgenerator.model.GeoPoint

enum class MapSelectionMode {
    SET_START,
    SET_END,
    VIEW_ONLY
}

@Composable
fun OsmMapView(
    startPoint: GeoPoint?,
    endPoint: GeoPoint?,
    routeCandidate: RouteCandidate?,
    selectionMode: MapSelectionMode,
    onPointSelected: (GeoPoint) -> Unit,
    onStartPointSet: ((GeoPoint) -> Unit)? = null,
    onEndPointSet: ((GeoPoint) -> Unit)? = null,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(280.dp)
        .clipToBounds()
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            Configuration.getInstance().userAgentValue = context.packageName

            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)

                controller.setZoom(7.5)
                val centerPt = startPoint ?: GeoPoint(49.9961, 20.0358)
                controller.setCenter(OsmGeoPoint(centerPt.latitude, centerPt.longitude))
            }
        },
        update = { mapView ->
            mapView.overlays.clear()

            // 1. Rejestracja zdarzeń kliknięcia na mapie
            val mapEventsReceiver = object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: OsmGeoPoint?): Boolean {
                    if (p != null && selectionMode != MapSelectionMode.VIEW_ONLY) {
                        onPointSelected(GeoPoint(p.latitude, p.longitude))
                        return true
                    }
                    return false
                }

                override fun longPressHelper(p: OsmGeoPoint?): Boolean = false
            }
            mapView.overlays.add(MapEventsOverlay(mapEventsReceiver))

            // 2. Rysowanie śladu wyznaczonej trasy (Polyline)
            routeCandidate?.let { candidate ->
                if (candidate.roadPoints.isNotEmpty()) {
                    val polyline = Polyline(mapView).apply {
                        outlinePaint.color = android.graphics.Color.RED
                        outlinePaint.strokeWidth = 10f
                        setPoints(candidate.roadPoints.map { OsmGeoPoint(it.latitude, it.longitude) })
                    }
                    mapView.overlays.add(polyline)
                }
            }

            // 3. Znacznik Start (Przesuwalny palcem)
            startPoint?.let { pt ->
                val startMarker = Marker(mapView).apply {
                    position = OsmGeoPoint(pt.latitude, pt.longitude)
                    title = "Start (Przeciągnij palcem)"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    isDraggable = true
                    setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                        override fun onMarkerDrag(marker: Marker?) {}
                        override fun onMarkerDragStart(marker: Marker?) {}
                        override fun onMarkerDragEnd(marker: Marker?) {
                            marker?.position?.let { p ->
                                val newPt = GeoPoint(p.latitude, p.longitude)
                                onStartPointSet?.invoke(newPt) ?: onPointSelected(newPt)
                            }
                        }
                    })
                }
                mapView.overlays.add(startMarker)
            }

            // 4. Znacznik Meta (Przesuwalny palcem)
            endPoint?.let { pt ->
                val endMarker = Marker(mapView).apply {
                    position = OsmGeoPoint(pt.latitude, pt.longitude)
                    title = "Meta (Przeciągnij palcem)"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    isDraggable = true
                    setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                        override fun onMarkerDrag(marker: Marker?) {}
                        override fun onMarkerDragStart(marker: Marker?) {}
                        override fun onMarkerDragEnd(marker: Marker?) {
                            marker?.position?.let { p ->
                                val newPt = GeoPoint(p.latitude, p.longitude)
                                onEndPointSet?.invoke(newPt) ?: onPointSelected(newPt)
                            }
                        }
                    })
                }
                mapView.overlays.add(endMarker)
            }

            mapView.invalidate()
        }
    )
}
