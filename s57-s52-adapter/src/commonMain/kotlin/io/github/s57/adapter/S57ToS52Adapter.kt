package io.github.s57.adapter

import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import io.github.s57.core.S57Value

/**
 * JS-safe S-57 -> portrayal preparation boundary.
 *
 * This module deliberately does not import `the S-52 packages` in commonMain. The
 * real S-52 WebGL bridge lives in `s57-render-webgl/jsMain`, where the browser
 * renderer can compile against the actual S-52 source/composite build. Keeping
 * this common adapter local avoids the Kotlin/JS FIR crash that happens when
 * the metadata/JVM S-52 artifacts are analyzed from a common source set.
 */
class S57ToS52Adapter {
    fun adapt(feature: S57Feature): S52AdapterFeature = S52AdapterFeature(
        id = feature.id,
        objectClassAcronym = feature.objectClass.uppercase(),
        attributes = feature.attributes.keys.sorted(),
        geometryType = feature.geometry::class.simpleName ?: "Unknown"
    )

    fun adaptFeature(feature: S57Feature): S57ToS52Result = adaptFeatures(listOf(feature))

    fun adaptFeatures(features: List<S57Feature>): S57ToS52Result {
        val diagnostics = mutableListOf<S57ToS52Diagnostic>()
        val portrayalFeatures = features.mapNotNull { feature -> feature.toPortrayalFeature(diagnostics) }
        return S57ToS52Result(portrayalFeatures, diagnostics)
    }

    fun transcript(result: S57ToS52Result): String = buildString {
        append("features=${result.features.size}")
        for (feature in result.features) {
            append("\n${feature.id}:${feature.objectClassAcronym}:${feature.primitive}:${feature.attributes.keys.sorted().joinToString(",")}")
        }
        if (result.diagnostics.isNotEmpty()) {
            append("\ndiagnostics=${result.diagnostics.size}")
            result.diagnostics.forEach { append("\n${it.severity}: feature=${it.featureId} ${it.message}") }
        }
    }

    private fun S57Feature.toPortrayalFeature(diagnostics: MutableList<S57ToS52Diagnostic>): S57PortrayalFeature? {
        val primitive = geometry.toPortrayalPrimitive()
        if (primitive == null) {
            diagnostics += S57ToS52Diagnostic(id, S57ToS52DiagnosticSeverity.Warning, "Feature has no renderable geometry")
            return null
        }
        val geometryModel = geometry.toPortrayalGeometry(id, diagnostics) ?: return null
        return S57PortrayalFeature(
            id = id,
            objectClassAcronym = objectClass.uppercase(),
            primitive = primitive,
            attributes = attributes.mapValues { (_, value) -> value.toPortrayalValue() },
            geometry = geometryModel,
            scaleMin = attributes["SCAMIN"]?.asIntOrNull(),
            scaleMax = attributes["SCAMAX"]?.asIntOrNull()
        )
    }
}

private fun S57Geometry.toPortrayalPrimitive(): S57PortrayalPrimitive? = when (this) {
    S57Geometry.Empty -> null
    is S57Geometry.Point -> S57PortrayalPrimitive.Point
    is S57Geometry.MultiPoint -> S57PortrayalPrimitive.Point
    is S57Geometry.LineString -> S57PortrayalPrimitive.Line
    is S57Geometry.Polygon -> S57PortrayalPrimitive.Area
    is S57Geometry.MultiPolygon -> S57PortrayalPrimitive.Area
}

private fun S57Geometry.toPortrayalGeometry(
    featureId: Long,
    diagnostics: MutableList<S57ToS52Diagnostic>
): S57PortrayalGeometry? = when (this) {
    S57Geometry.Empty -> null
    is S57Geometry.Point -> S57PortrayalGeometry.Point(S57PortrayalCoordinate(coordinate.lon, coordinate.lat))
    is S57Geometry.MultiPoint -> S57PortrayalGeometry.MultiPoint(points.map { S57PortrayalCoordinate(it.lon, it.lat) })
    is S57Geometry.LineString -> S57PortrayalGeometry.LineString(points.map { S57PortrayalCoordinate(it.lon, it.lat) })
    is S57Geometry.Polygon -> {
        val outer = rings.firstOrNull().orEmpty()
        if (outer.size < 3) {
            diagnostics += S57ToS52Diagnostic(featureId, S57ToS52DiagnosticSeverity.Warning, "Polygon has no valid outer ring")
            null
        } else {
            S57PortrayalGeometry.Polygon(
                outer = outer.map { S57PortrayalCoordinate(it.lon, it.lat) },
                holes = rings.drop(1).filter { it.size >= 3 }.map { ring -> ring.map { S57PortrayalCoordinate(it.lon, it.lat) } }
            )
        }
    }
    is S57Geometry.MultiPolygon -> {
        val first = polygons.firstOrNull()
        if (first == null) {
            diagnostics += S57ToS52Diagnostic(featureId, S57ToS52DiagnosticSeverity.Warning, "MultiPolygon is empty")
            null
        } else {
            diagnostics += S57ToS52Diagnostic(featureId, S57ToS52DiagnosticSeverity.Info, "MultiPolygon flattened to first polygon for the current portrayal bridge")
            first.toPortrayalGeometry(featureId, diagnostics)
        }
    }
}

private fun S57Value.toPortrayalValue(): S57PortrayalValue = when (this) {
    S57Value.Empty -> S57PortrayalValue.Empty()
    is S57Value.Text -> S57PortrayalValue.Text(value)
    is S57Value.Integer -> S57PortrayalValue.Integer(value)
    is S57Value.Decimal -> S57PortrayalValue.Decimal(value)
    is S57Value.ListValue -> S57PortrayalValue.ListValue(values.map { it.toPortrayalValue() })
}

private fun S57Value.asIntOrNull(): Int? = when (this) {
    is S57Value.Integer -> value
    is S57Value.Decimal -> value.toInt()
    is S57Value.Text -> value.toIntOrNull()
    is S57Value.ListValue -> values.firstOrNull()?.asIntOrNull()
    S57Value.Empty -> null
}

data class S52AdapterFeature(
    val id: Long,
    val objectClassAcronym: String,
    val attributes: List<String>,
    val geometryType: String
)

data class S57ToS52Result(
    val features: List<S57PortrayalFeature>,
    val diagnostics: List<S57ToS52Diagnostic>
) {
    val skippedCount: Int get() = diagnostics.count { it.severity == S57ToS52DiagnosticSeverity.Warning }
}

data class S57PortrayalFeature(
    val id: Long,
    val objectClassAcronym: String,
    val primitive: S57PortrayalPrimitive,
    val attributes: Map<String, S57PortrayalValue>,
    val geometry: S57PortrayalGeometry,
    val scaleMin: Int? = null,
    val scaleMax: Int? = null
)

enum class S57PortrayalPrimitive {
    Point,
    Line,
    Area
}

data class S57PortrayalCoordinate(
    val lon: Double,
    val lat: Double
)

sealed interface S57PortrayalGeometry {
    data class Point(val coordinate: S57PortrayalCoordinate) : S57PortrayalGeometry
    data class MultiPoint(val coordinates: List<S57PortrayalCoordinate>) : S57PortrayalGeometry
    data class LineString(val coordinates: List<S57PortrayalCoordinate>) : S57PortrayalGeometry
    data class Polygon(
        val outer: List<S57PortrayalCoordinate>,
        val holes: List<List<S57PortrayalCoordinate>> = emptyList()
    ) : S57PortrayalGeometry
}

sealed interface S57PortrayalValue {
    class Empty : S57PortrayalValue
    data class Text(val value: String) : S57PortrayalValue
    data class Integer(val value: Int) : S57PortrayalValue
    data class Decimal(val value: Double) : S57PortrayalValue
    data class ListValue(val values: List<S57PortrayalValue>) : S57PortrayalValue
}

data class S57ToS52Diagnostic(
    val featureId: Long,
    val severity: S57ToS52DiagnosticSeverity,
    val message: String
)

enum class S57ToS52DiagnosticSeverity {
    Info,
    Warning
}
