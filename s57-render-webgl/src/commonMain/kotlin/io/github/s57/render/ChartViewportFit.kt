package io.github.s57.render

import io.github.s57.core.GeoBounds
import io.github.s57.core.GeoPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Phase 20 viewport-fit helpers for first-render and zoom-to-cell behavior. */
data class ChartViewportFit(
    val sourceBounds: GeoBounds,
    val fittedBounds: GeoBounds,
    val cameraCenter: GeoPoint,
    val scaleDenominator: Double
)

fun fitChartBoundsToViewport(
    sourceBounds: GeoBounds,
    viewport: ScreenSize,
    paddingFraction: Double = 0.08
): GeoBounds {
    val safeWidth = viewport.widthPx.coerceAtLeast(1).toDouble()
    val safeHeight = viewport.heightPx.coerceAtLeast(1).toDouble()
    val targetAspect = safeWidth / safeHeight
    val center = sourceBounds.center()
    var lonSpan = max(1.0e-9, sourceBounds.maxLon - sourceBounds.minLon)
    var latSpan = max(1.0e-9, sourceBounds.maxLat - sourceBounds.minLat)

    val currentAspect = lonSpan / latSpan
    if (currentAspect < targetAspect) {
        lonSpan = latSpan * targetAspect
    } else if (currentAspect > targetAspect) {
        latSpan = lonSpan / targetAspect
    }

    val padding = paddingFraction.coerceIn(0.0, 0.45)
    val paddingScale = if (padding <= 0.0) 1.0 else 1.0 / (1.0 - 2.0 * padding)
    lonSpan *= paddingScale
    latSpan *= paddingScale

    return GeoBounds(
        minLon = center.lon - lonSpan / 2.0,
        minLat = center.lat - latSpan / 2.0,
        maxLon = center.lon + lonSpan / 2.0,
        maxLat = center.lat + latSpan / 2.0
    )
}

fun chartViewportFitForBounds(
    sourceBounds: GeoBounds,
    viewport: ScreenSize,
    paddingFraction: Double = 0.08
): ChartViewportFit {
    val fitted = fitChartBoundsToViewport(sourceBounds, viewport, paddingFraction)
    return ChartViewportFit(
        sourceBounds = sourceBounds,
        fittedBounds = fitted,
        cameraCenter = fitted.center(),
        scaleDenominator = estimateScaleDenominator(fitted, viewport)
    )
}

fun estimateScaleDenominator(bounds: GeoBounds, viewport: ScreenSize): Double {
    val centerLat = bounds.center().lat.coerceIn(-85.0, 85.0)
    val metersPerLonDegree = METERS_PER_LON_DEGREE_AT_EQUATOR * cos(centerLat * PI / 180.0).coerceAtLeast(0.01)
    val widthMeters = max(1.0, (bounds.maxLon - bounds.minLon) * metersPerLonDegree)
    val heightMeters = max(1.0, (bounds.maxLat - bounds.minLat) * METERS_PER_LAT_DEGREE)
    val metersPerPixel = max(
        widthMeters / viewport.widthPx.coerceAtLeast(1).toDouble(),
        heightMeters / viewport.heightPx.coerceAtLeast(1).toDouble()
    )
    return (metersPerPixel / CSS_PIXEL_METERS).coerceIn(500.0, 50_000_000.0)
}

fun GeoBounds.center(): GeoPoint = GeoPoint(
    lon = (minLon + maxLon) / 2.0,
    lat = (minLat + maxLat) / 2.0
)

private const val CSS_PIXEL_METERS = 0.0002645833333333333
private const val METERS_PER_LAT_DEGREE = 110_574.0
private const val METERS_PER_LON_DEGREE_AT_EQUATOR = 111_320.0
