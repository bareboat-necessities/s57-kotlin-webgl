package io.github.s57.render

import io.github.s57.core.GeoBounds
import io.github.s57.core.GeoPoint
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import io.github.s57.core.S57Value
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

sealed class ProjectedGeometry {
    class Empty : ProjectedGeometry()
    data class Point(val point: ScreenPoint) : ProjectedGeometry()
    data class MultiPoint(val points: List<ScreenPoint>) : ProjectedGeometry()
    data class LineString(val points: List<ScreenPoint>) : ProjectedGeometry()
    data class Polygon(val rings: List<List<ScreenPoint>>) : ProjectedGeometry()
    data class MultiPolygon(val polygons: List<Polygon>) : ProjectedGeometry()
}

data class ProjectedFeature(
    val featureId: Long,
    val objectClass: String,
    val displayName: String = objectClass,
    val geometry: ProjectedGeometry,
    val geoBounds: GeoBounds?,
    val screenBounds: ScreenBounds?,
    val feature: S57Feature? = null
)


internal fun ProjectedFeature.soundingLabel(pointIndex: Int = 0): String? {
    if (!objectClass.equals("SOUNDG", ignoreCase = true)) return null
    val value = feature?.attributes?.get("VALSOU") ?: return null
    return value.soundingValueAt(pointIndex)?.formatSoundingValue()
}

private fun S57Value.soundingValueAt(pointIndex: Int): S57Value? = when (this) {
    is S57Value.ListValue -> values.getOrNull(pointIndex) ?: values.firstOrNull()
    is S57Value.Text -> value.split(',', ';', '|')
        .mapNotNull { token -> token.trim().takeIf { it.isNotEmpty() } }
        .let { tokens -> tokens.getOrNull(pointIndex) ?: tokens.firstOrNull() }
        ?.let(S57Value::Text)
    S57Value.Empty -> null
    else -> this
}

private fun S57Value.formatSoundingValue(): String? = when (this) {
    S57Value.Empty -> null
    is S57Value.Integer -> value.toString()
    is S57Value.Decimal -> value.formatSoundingDecimal()
    is S57Value.Text -> value.trim().takeIf { it.isNotEmpty() }
    is S57Value.ListValue -> values.firstOrNull()?.formatSoundingValue()
}

private fun Double.formatSoundingDecimal(): String {
    val whole = toLong()
    return if (this == whole.toDouble()) whole.toString() else toString().trimEnd('0').trimEnd('.')
}

data class ScreenBounds(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
) {
    fun intersects(point: ScreenPoint, radiusPx: Double): Boolean =
        point.x >= minX - radiusPx && point.x <= maxX + radiusPx && point.y >= minY - radiusPx && point.y <= maxY + radiusPx

    fun intersects(other: ScreenBounds): Boolean =
        minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY

    fun isInside(widthPx: Int, heightPx: Int): Boolean =
        minX >= 0.0 && minY >= 0.0 && maxX <= widthPx.toDouble() && maxY <= heightPx.toDouble()
}

fun screenBoundsFrom(points: List<ScreenPoint>): ScreenBounds? {
    if (points.isEmpty()) return null
    var minX = points.first().x
    var maxX = points.first().x
    var minY = points.first().y
    var maxY = points.first().y
    for (point in points.drop(1)) {
        minX = min(minX, point.x)
        maxX = max(maxX, point.x)
        minY = min(minY, point.y)
        maxY = max(maxY, point.y)
    }
    return ScreenBounds(minX, minY, maxX, maxY)
}


/**
 * Stable, chart-like paint order for decoded geometry fallbacks and snapshots.
 *
 * Raw ENC feature order is optimized for interchange, not visual legibility. If
 * we paint features in decode order, late area objects can cover contours,
 * soundings, and aids-to-navigation, producing snapshots that look like random
 * blobs instead of a readable chart. This ordering keeps broad fills below line
 * work and point symbols, with land/coast detail above sea/depth fills.
 */
fun List<ProjectedFeature>.sortedForChartReadability(): List<ProjectedFeature> = sortedWith(
    compareBy<ProjectedFeature> { it.chartReadabilityLayer() }
        .thenBy { it.featureId }
        .thenBy { it.objectClass }
)

private fun ProjectedFeature.chartReadabilityLayer(): Int {
    val objectClass = objectClass.uppercase()
    val geometryLayer = when (geometry) {
        is ProjectedGeometry.Polygon,
        is ProjectedGeometry.MultiPolygon -> 100
        is ProjectedGeometry.LineString -> 300
        is ProjectedGeometry.Point,
        is ProjectedGeometry.MultiPoint -> 500
        is ProjectedGeometry.Empty -> 900
    }
    val classOffset = when (objectClass) {
        "DEPARE", "DRGARE", "UNSARE", "SEAARE" -> 0
        "LNDARE" -> 60
        "LAKARE", "RIVERS" -> 70
        "FAIRWY", "ACHARE", "RESARE", "RECTRC", "TSSBND", "TSSLPT" -> 90
        "SLCONS", "PONTON", "DOCARE" -> 110
        "DEPCNT", "DRVAL1", "DRVAL2" -> 0
        "COALNE", "SLOTOP" -> 30
        "CBLARE", "CBLOHD", "CBLSUB", "PIPARE", "PIPOHD", "PIPSOL" -> 45
        "BOYLAT", "BOYCAR", "BOYSAW", "BOYISD", "BOYSPP", "BOYINB",
        "BCNLAT", "BCNCAR", "BCNSAW", "BCNSPP", "BCNISD", "LIGHTS" -> 80
        else -> 50
    }
    return geometryLayer + classOffset
}

data class StaticChartFrame(
    val request: ChartRenderRequest,
    val queriedFeatureCount: Int,
    val adaptedFeatureCount: Int,
    val projectedFeatures: List<ProjectedFeature>,
    val adapterDiagnostics: List<String> = emptyList(),
    val centerCrosshairHits: List<ChartHitResult> = emptyList(),
    val depthMesh: DepthMeshTile? = null
) {
    val featureCount: Int get() = projectedFeatures.size
    val onscreenFeatureCount: Int get() = projectedFeatures.count { it.screenBounds?.intersects(viewportBounds()) == true }
    val offscreenFeatureCount: Int get() = projectedFeatures.count { it.screenBounds != null && it.screenBounds.intersects(viewportBounds()).not() }
    val clippedFeatureCount: Int get() = projectedFeatures.count { feature ->
        val screen = feature.screenBounds
        screen != null && screen.intersects(viewportBounds()) && !screen.isInside(request.widthPx, request.heightPx)
    }

    fun summary(): RenderedFrameSummary = RenderedFrameSummary(
        widthPx = request.widthPx,
        heightPx = request.heightPx,
        message = "Phase 20 static chart frame cells=${request.renderCellIds.size} features=$featureCount queried=$queriedFeatureCount adapted=$adaptedFeatureCount onscreen=$onscreenFeatureCount offscreen=$offscreenFeatureCount clipped=$clippedFeatureCount mode=${request.renderMode}",
        camera = request.camera,
        centerCrosshairHits = centerCrosshairHits,
        depthMeshEnabled = depthMesh != null || request.depthMesh.enabled
    )

    fun hitTester(): ChartHitTester = StaticChartFrameHitTester(projectedFeatures)

    fun viewportBounds(): ScreenBounds = ScreenBounds(0.0, 0.0, request.widthPx.toDouble(), request.heightPx.toDouble())
}

class StaticChartFrameHitTester(
    private val features: List<ProjectedFeature>
) : ChartHitTester {
    override fun hitTest(point: ScreenPoint, radiusPx: Double): List<ChartHitResult> = features.asSequence()
        .filter { it.screenBounds?.intersects(point, radiusPx) == true }
        .mapNotNull { feature ->
            val distance = feature.distanceTo(point)
            if (distance <= radiusPx) {
                ChartHitResult(
                    featureId = feature.featureId,
                    objectClass = feature.objectClass,
                    displayName = feature.displayName,
                    distancePx = distance,
                    feature = feature.feature
                )
            } else {
                null
            }
        }
        .sortedBy { it.distancePx }
        .toList()
}

fun S57Geometry.project(projection: ChartProjection): ProjectedGeometry = when (this) {
    S57Geometry.Empty -> ProjectedGeometry.Empty()
    is S57Geometry.Point -> ProjectedGeometry.Point(projection.project(coordinate))
    is S57Geometry.MultiPoint -> ProjectedGeometry.MultiPoint(points.map(projection::project))
    is S57Geometry.LineString -> ProjectedGeometry.LineString(points.map(projection::project))
    is S57Geometry.Polygon -> ProjectedGeometry.Polygon(rings.map { ring -> ring.map(projection::project) })
    is S57Geometry.MultiPolygon -> ProjectedGeometry.MultiPolygon(polygons.map { polygon -> polygon.project(projection) as ProjectedGeometry.Polygon })
}

fun ProjectedGeometry.points(): List<ScreenPoint> = when (this) {
    is ProjectedGeometry.Empty -> emptyList()
    is ProjectedGeometry.Point -> listOf(point)
    is ProjectedGeometry.MultiPoint -> points
    is ProjectedGeometry.LineString -> points
    is ProjectedGeometry.Polygon -> rings.flatten()
    is ProjectedGeometry.MultiPolygon -> polygons.flatMap { it.points() }
}

private fun ProjectedFeature.distanceTo(point: ScreenPoint): Double = when (val g = geometry) {
    is ProjectedGeometry.Empty -> Double.POSITIVE_INFINITY
    is ProjectedGeometry.Point -> distance(g.point, point)
    is ProjectedGeometry.MultiPoint -> g.points.minOfOrNull { distance(it, point) } ?: Double.POSITIVE_INFINITY
    is ProjectedGeometry.LineString -> distanceToPolyline(g.points, point)
    is ProjectedGeometry.Polygon -> if (g.rings.firstOrNull()?.let { pointInPolygon(point, it) } == true) 0.0 else distanceToPolyline(g.rings.firstOrNull().orEmpty(), point)
    is ProjectedGeometry.MultiPolygon -> g.polygons.minOfOrNull { polygon ->
        val f = copy(geometry = polygon)
        f.distanceTo(point)
    } ?: Double.POSITIVE_INFINITY
}

private fun distance(a: ScreenPoint, b: ScreenPoint): Double = hypot(a.x - b.x, a.y - b.y)

private fun distanceToPolyline(points: List<ScreenPoint>, p: ScreenPoint): Double {
    if (points.isEmpty()) return Double.POSITIVE_INFINITY
    if (points.size == 1) return distance(points.first(), p)
    var best = Double.POSITIVE_INFINITY
    for (i in 0 until points.lastIndex) best = min(best, distanceToSegment(points[i], points[i + 1], p))
    return best
}

private fun distanceToSegment(a: ScreenPoint, b: ScreenPoint, p: ScreenPoint): Double {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len2 = dx * dx + dy * dy
    if (len2 <= 1.0e-12) return distance(a, p)
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
    return distance(ScreenPoint(a.x + t * dx, a.y + t * dy), p)
}

private fun pointInPolygon(point: ScreenPoint, ring: List<ScreenPoint>): Boolean {
    if (ring.size < 3) return false
    var inside = false
    var j = ring.lastIndex
    for (i in ring.indices) {
        val pi = ring[i]
        val pj = ring[j]
        val intersects = ((pi.y > point.y) != (pj.y > point.y)) &&
            (point.x < (pj.x - pi.x) * (point.y - pi.y) / ((pj.y - pi.y).takeIf { abs(it) > 1.0e-12 } ?: 1.0e-12) + pi.x)
        if (intersects) inside = !inside
        j = i
    }
    return inside
}
