package io.github.s57.adapter

import io.github.s57.core.GeoPoint
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import io.github.s57.core.S57Value

/**
 * Phase 9-safe bridge model from decoded S-57 features toward S-52 portrayal.
 *
 * The public common/JS source set intentionally does not import S-52
 * classes directly. The v0.3.0 S-52 release artifact currently provides JVM and
 * metadata artifacts, but not a JS klib consumable by Kotlin/JS. Keeping this
 * module's common API S-52-shaped but dependency-light lets the browser build
 * compile while preserving a clean boundary for a future direct S-52 renderer
 * binding when a JS artifact is published.
 */
class S57ToS52Adapter {
    /** Compatibility summary retained from Phase 0 tests and simple diagnostics. */
    fun adapt(feature: S57Feature): S52AdapterFeature = S52AdapterFeature(
        id = feature.id,
        objectClassAcronym = feature.objectClass,
        attributes = feature.attributes.keys.sorted(),
        geometryType = feature.geometry::class.simpleName ?: "Unknown"
    )

    fun adaptFeatures(features: List<S57Feature>): S57ToS52Result {
        val diagnostics = mutableListOf<S57ToS52Diagnostic>()
        val adapted = features.mapNotNull { feature -> adaptFeature(feature, diagnostics) }
        return S57ToS52Result(adapted, diagnostics)
    }

    fun adaptFeature(feature: S57Feature): S57ToS52Result {
        val diagnostics = mutableListOf<S57ToS52Diagnostic>()
        val adapted = adaptFeature(feature, diagnostics)
        return S57ToS52Result(listOfNotNull(adapted), diagnostics)
    }

    fun transcript(result: S57ToS52Result): String = buildString {
        appendLine("S57ToS52Result features=${result.features.size} diagnostics=${result.diagnostics.size}")
        for (feature in result.features) {
            appendLine("feature ${feature.id} ${feature.objectClassAcronym} ${feature.primitive} attrs=${feature.attributes.keys.sorted().joinToString(",")}")
        }
        for (diagnostic in result.diagnostics) {
            appendLine("${diagnostic.severity}: feature=${diagnostic.featureId} ${diagnostic.message}")
        }
    }.trimEnd()

    private fun adaptFeature(feature: S57Feature, diagnostics: MutableList<S57ToS52Diagnostic>): S57PortrayalFeature? {
        val primitive = feature.geometry.toPortrayalPrimitive()
        if (primitive == null) {
            diagnostics += S57ToS52Diagnostic(feature.id, S57ToS52DiagnosticSeverity.Warning, "Feature has no renderable geometry")
            return null
        }

        val normalizedObjectClass = feature.objectClass.trim().uppercase()
        if (normalizedObjectClass.isBlank()) {
            diagnostics += S57ToS52Diagnostic(feature.id, S57ToS52DiagnosticSeverity.Warning, "Missing S-57 object class")
            return null
        }
        if (normalizedObjectClass !in commonRenderableObjectClasses) {
            diagnostics += S57ToS52Diagnostic(feature.id, S57ToS52DiagnosticSeverity.Info, "Object class '$normalizedObjectClass' is not in the common fast lookup table; preserving acronym dynamically")
        }

        return S57PortrayalFeature(
            id = feature.id,
            objectClassAcronym = normalizedObjectClass,
            primitive = primitive,
            attributes = feature.attributes.mapKeys { it.key.uppercase() }.mapValues { it.value.toPortrayalValue() },
            geometry = feature.geometry.toPortrayalGeometry(feature.id, diagnostics) ?: return null,
            scaleMin = feature.attributes["SCAMIN"]?.asIntOrNull(),
            scaleMax = feature.attributes["SCAMAX"]?.asIntOrNull()
        )
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
        is S57Geometry.Point -> S57PortrayalGeometry.Point(coordinate.toPortrayalCoordinate())
        is S57Geometry.MultiPoint -> S57PortrayalGeometry.MultiPoint(points.map { it.toPortrayalCoordinate() })
        is S57Geometry.LineString -> S57PortrayalGeometry.LineString(points.map { it.toPortrayalCoordinate() })
        is S57Geometry.Polygon -> {
            val outer = rings.firstOrNull().orEmpty()
            if (outer.size < 3) {
                diagnostics += S57ToS52Diagnostic(featureId, S57ToS52DiagnosticSeverity.Warning, "Polygon has no valid outer ring")
                null
            } else {
                S57PortrayalGeometry.Polygon(
                    outer = outer.map { it.toPortrayalCoordinate() },
                    holes = rings.drop(1).filter { it.size >= 3 }.map { ring -> ring.map { it.toPortrayalCoordinate() } }
                )
            }
        }
        is S57Geometry.MultiPolygon -> S57PortrayalGeometry.MultiPolygon(polygons.mapNotNull { polygon -> polygon.toPortrayalGeometry(featureId, diagnostics) as? S57PortrayalGeometry.Polygon })
    }

    private fun GeoPoint.toPortrayalCoordinate(): S57PortrayalCoordinate = S57PortrayalCoordinate(lon = lon, lat = lat)

    private fun S57Value.toPortrayalValue(): S57PortrayalValue = when (this) {
        S57Value.Empty -> S57PortrayalValue.Empty
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

    private companion object {
        val commonRenderableObjectClasses = setOf(
            "DEPARE", "DEPCNT", "SOUNDG", "BOYLAT", "BCNLAT", "LIGHTS", "WRECKS", "OBSTRN",
            "COALNE", "LNDARE", "M_COVR", "M_NSYS", "M_QUAL", "UWTROC", "SBDARE", "PONTON"
        )
    }
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

sealed class S57PortrayalGeometry {
    data class Point(val coordinate: S57PortrayalCoordinate) : S57PortrayalGeometry()
    data class MultiPoint(val points: List<S57PortrayalCoordinate>) : S57PortrayalGeometry()
    data class LineString(val points: List<S57PortrayalCoordinate>) : S57PortrayalGeometry()
    data class Polygon(val outer: List<S57PortrayalCoordinate>, val holes: List<List<S57PortrayalCoordinate>> = emptyList()) : S57PortrayalGeometry()
    data class MultiPolygon(val polygons: List<Polygon>) : S57PortrayalGeometry()
}

sealed class S57PortrayalValue {
    data object Empty : S57PortrayalValue()
    data class Text(val value: String) : S57PortrayalValue()
    data class Integer(val value: Int) : S57PortrayalValue()
    data class Decimal(val value: Double) : S57PortrayalValue()
    data class ListValue(val values: List<S57PortrayalValue>) : S57PortrayalValue()

    fun asDoubleOrNull(): Double? = when (this) {
        is Decimal -> value
        is Integer -> value.toDouble()
        is Text -> value.toDoubleOrNull()
        is ListValue -> values.firstOrNull()?.asDoubleOrNull()
        Empty -> null
    }

    fun asTextOrNull(): String? = when (this) {
        is Text -> value
        is Integer -> value.toString()
        is Decimal -> value.toString()
        is ListValue -> values.firstOrNull()?.asTextOrNull()
        Empty -> null
    }
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
