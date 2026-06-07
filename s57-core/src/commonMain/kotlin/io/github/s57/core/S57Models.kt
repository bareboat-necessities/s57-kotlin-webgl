package io.github.s57.core

data class S57CellSummary(
    val cellId: String,
    val name: String,
    val edition: Int? = null,
    val updateNumber: Int? = null,
    val bounds: GeoBounds? = null,
    val featureCount: Int = 0
)

data class S57Dataset(
    val summary: S57CellSummary,
    val features: List<S57Feature>
)

data class S57Feature(
    val id: Long,
    val objectClass: String,
    val attributes: Map<String, S57Value> = emptyMap(),
    val geometry: S57Geometry = S57Geometry.Empty,
    val bounds: GeoBounds? = geometry.boundsOrNull()
)

sealed class S57Value {
    data class Text(val value: String) : S57Value()
    data class Integer(val value: Int) : S57Value()
    data class Decimal(val value: Double) : S57Value()
    data class ListValue(val values: List<S57Value>) : S57Value()
    data object Empty : S57Value()
}

sealed class S57Geometry {
    data object Empty : S57Geometry()
    data class Point(val coordinate: GeoPoint) : S57Geometry()
    data class MultiPoint(val points: List<GeoPoint>) : S57Geometry()
    data class LineString(val points: List<GeoPoint>) : S57Geometry()
    data class Polygon(val rings: List<List<GeoPoint>>) : S57Geometry()
    data class MultiPolygon(val polygons: List<Polygon>) : S57Geometry()
}

data class GeoPoint(val lon: Double, val lat: Double)

data class GeoBounds(
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double
) {
    fun intersects(other: GeoBounds): Boolean =
        minLon <= other.maxLon && maxLon >= other.minLon && minLat <= other.maxLat && maxLat >= other.minLat

    companion object {
        fun fromPoints(points: List<GeoPoint>): GeoBounds? {
            if (points.isEmpty()) return null
            var minLon = points.first().lon
            var maxLon = points.first().lon
            var minLat = points.first().lat
            var maxLat = points.first().lat
            for (point in points.drop(1)) {
                minLon = kotlin.math.min(minLon, point.lon)
                maxLon = kotlin.math.max(maxLon, point.lon)
                minLat = kotlin.math.min(minLat, point.lat)
                maxLat = kotlin.math.max(maxLat, point.lat)
            }
            return GeoBounds(minLon, minLat, maxLon, maxLat)
        }
    }
}

fun S57Geometry.boundsOrNull(): GeoBounds? = when (this) {
    S57Geometry.Empty -> null
    is S57Geometry.Point -> GeoBounds(coordinate.lon, coordinate.lat, coordinate.lon, coordinate.lat)
    is S57Geometry.MultiPoint -> GeoBounds.fromPoints(points)
    is S57Geometry.LineString -> GeoBounds.fromPoints(points)
    is S57Geometry.Polygon -> GeoBounds.fromPoints(rings.flatten())
    is S57Geometry.MultiPolygon -> GeoBounds.fromPoints(polygons.flatMap { it.rings.flatten() })
}
