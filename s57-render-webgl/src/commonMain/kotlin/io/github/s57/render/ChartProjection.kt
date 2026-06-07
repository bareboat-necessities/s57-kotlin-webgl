package io.github.s57.render

import io.github.s57.core.GeoBounds
import io.github.s57.core.GeoPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Deterministic lon/lat-to-screen projection for one static chart view.
 *
 * This is deliberately simple and local to a requested chart bounds. It is not a
 * chartplotter pan/zoom controller and it does not permanently transform stored
 * geometry. Rotation and small tilt are applied only for the rendered frame.
 */
class ChartProjection(
    val bounds: GeoBounds,
    val viewport: ScreenSize,
    val rotationDegrees: Double = 0.0,
    val tiltDegrees: Double = 0.0
) {
    private val lonSpan = max(1.0e-12, bounds.maxLon - bounds.minLon)
    private val latSpan = max(1.0e-12, bounds.maxLat - bounds.minLat)
    private val cx = viewport.widthPx / 2.0
    private val cy = viewport.heightPx / 2.0
    private val rotationRad = -rotationDegrees * PI / 180.0
    private val tiltScale = cos(tiltDegrees.coerceIn(0.0, 65.0) * PI / 180.0).coerceIn(0.42, 1.0)

    fun project(point: GeoPoint): ScreenPoint {
        val x0 = ((point.lon - bounds.minLon) / lonSpan) * viewport.widthPx
        val y0 = (1.0 - ((point.lat - bounds.minLat) / latSpan)) * viewport.heightPx
        val dx = x0 - cx
        val dy = (y0 - cy) * tiltScale
        val rx = dx * cos(rotationRad) - dy * sin(rotationRad)
        val ry = dx * sin(rotationRad) + dy * cos(rotationRad)
        return ScreenPoint(cx + rx, cy + ry)
    }

    fun unproject(screen: ScreenPoint): GeoPoint {
        val dx = screen.x - cx
        val dy = screen.y - cy
        val ux = dx * cos(-rotationRad) - dy * sin(-rotationRad)
        val uy = dx * sin(-rotationRad) + dy * cos(-rotationRad)
        val untiltedY = if (tiltScale == 0.0) uy else uy / tiltScale
        val x0 = cx + ux
        val y0 = cy + untiltedY
        val lon = bounds.minLon + (x0 / viewport.widthPx.toDouble()) * lonSpan
        val lat = bounds.minLat + (1.0 - y0 / viewport.heightPx.toDouble()) * latSpan
        return GeoPoint(lon.coerceIn(min(bounds.minLon, bounds.maxLon), max(bounds.minLon, bounds.maxLon)), lat.coerceIn(min(bounds.minLat, bounds.maxLat), max(bounds.minLat, bounds.maxLat)))
    }

    companion object {
        fun from(request: ChartRenderRequest): ChartProjection = ChartProjection(
            bounds = request.bounds,
            viewport = ScreenSize(request.widthPx, request.heightPx),
            rotationDegrees = request.camera.rotationDegrees,
            tiltDegrees = if (request.renderMode == ChartRenderMode.Flat2D) 0.0 else request.camera.tiltDegrees
        )
    }
}
