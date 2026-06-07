package io.github.s57.render

import io.github.s57.core.GeoPoint
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import io.github.s57.core.S57Value

fun List<S57Feature>.extractDepthSamples(): List<DepthSample> = flatMap { feature -> feature.extractDepthSamples() }

fun S57Feature.extractDepthSamples(): List<DepthSample> {
    val depth = attributes["VALSOU"]?.asDoubleOrNull()
        ?: attributes["DRVAL1"]?.asDoubleOrNull()
        ?: attributes["DRVAL2"]?.asDoubleOrNull()
        ?: return emptyList()
    return geometry.samplePoints().map { point -> DepthSample(point, depth, id) }
}

private fun S57Geometry.samplePoints(): List<GeoPoint> = when (this) {
    S57Geometry.Empty -> emptyList()
    is S57Geometry.Point -> listOf(coordinate)
    is S57Geometry.MultiPoint -> points
    is S57Geometry.LineString -> points
    is S57Geometry.Polygon -> rings.flatten()
    is S57Geometry.MultiPolygon -> polygons.flatMap { it.rings.flatten() }
}

private fun S57Value.asDoubleOrNull(): Double? = when (this) {
    is S57Value.Decimal -> value
    is S57Value.Integer -> value.toDouble()
    is S57Value.Text -> value.toDoubleOrNull()
    is S57Value.ListValue -> values.firstOrNull()?.asDoubleOrNull()
    S57Value.Empty -> null
}
