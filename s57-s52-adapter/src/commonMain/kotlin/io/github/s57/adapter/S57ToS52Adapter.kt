package io.github.s57.adapter

import io.github.s57.core.GeoPoint
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
        val portrayalFeatures = features.flatMap { feature -> feature.toPortrayalFeatures(diagnostics) }
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

    private fun S57Feature.toPortrayalFeatures(diagnostics: MutableList<S57ToS52Diagnostic>): List<S57PortrayalFeature> {
        val normalizedClass = objectClass.uppercase()
        if (normalizedClass.startsWith("OBJL_")) {
            diagnostics += S57ToS52Diagnostic(id, S57ToS52DiagnosticKind.UnsupportedObjectClass, S57ToS52DiagnosticSeverity.Warning, "Unknown object class $normalizedClass preserved for fallback portrayal")
        }
        val sourceGeometry = geometry
        return when {
            normalizedClass == "SOUNDG" && sourceGeometry is S57Geometry.MultiPoint -> splitSoundings(sourceGeometry.points, diagnostics)
            sourceGeometry is S57Geometry.MultiPolygon -> splitMultiPolygon(sourceGeometry.polygons, diagnostics)
            else -> listOfNotNull(toPortrayalFeature(id, sourceGeometry, attributes, diagnostics))
        }
    }

    private fun S57Feature.splitMultiPolygon(
        polygons: List<S57Geometry.Polygon>,
        diagnostics: MutableList<S57ToS52Diagnostic>
    ): List<S57PortrayalFeature> {
        if (polygons.isEmpty()) {
            diagnostics += S57ToS52Diagnostic(id, S57ToS52DiagnosticKind.Geometry, S57ToS52DiagnosticSeverity.Warning, "MultiPolygon is empty")
            return emptyList()
        }
        diagnostics += S57ToS52Diagnostic(id, S57ToS52DiagnosticKind.Geometry, S57ToS52DiagnosticSeverity.Info, "MultiPolygon split into ${polygons.size} portrayal feature(s)")
        return polygons.mapIndexedNotNull { index, polygon ->
            toPortrayalFeature(splitId(index), polygon, attributes, diagnostics)
        }
    }

    private fun S57Feature.splitSoundings(
        points: List<GeoPoint>,
        diagnostics: MutableList<S57ToS52Diagnostic>
    ): List<S57PortrayalFeature> {
        if (points.isEmpty()) {
            diagnostics += S57ToS52Diagnostic(id, S57ToS52DiagnosticKind.Geometry, S57ToS52DiagnosticSeverity.Warning, "SOUNDG multipoint is empty")
            return emptyList()
        }
        val values = attributes["VALSOU"]?.splitListValues().orEmpty()
        if (values.isNotEmpty() && values.size != points.size) {
            diagnostics += S57ToS52Diagnostic(id, S57ToS52DiagnosticKind.Attribute, S57ToS52DiagnosticSeverity.Warning, "SOUNDG VALSOU count ${values.size} does not match point count ${points.size}")
        }
        return points.mapIndexedNotNull { index, point ->
            val attrs = attributes.toMutableMap()
            val perPoint = values.getOrNull(index) ?: values.firstOrNull() ?: attributes["VALSOU"]
            if (perPoint != null) attrs["VALSOU"] = perPoint
            toPortrayalFeature(splitId(index), S57Geometry.Point(point), attrs, diagnostics)
        }
    }

    private fun S57Feature.toPortrayalFeature(
        portrayalId: Long,
        sourceGeometry: S57Geometry,
        sourceAttributes: Map<String, S57Value>,
        diagnostics: MutableList<S57ToS52Diagnostic>
    ): S57PortrayalFeature? {
        val primitive = sourceGeometry.toPortrayalPrimitive()
        if (primitive == null) {
            diagnostics += S57ToS52Diagnostic(id, S57ToS52DiagnosticKind.Geometry, S57ToS52DiagnosticSeverity.Warning, "Feature has no renderable geometry")
            return null
        }
        val geometryModel = sourceGeometry.toPortrayalGeometry(id, diagnostics) ?: return null
        val normalizedAttributes = sourceAttributes.mapKeys { (name, _) -> name.uppercase() }
        normalizedAttributes.keys
            .filter { it.startsWith("ATTL_") }
            .forEach { attr -> diagnostics += S57ToS52Diagnostic(id, S57ToS52DiagnosticKind.UnsupportedAttribute, S57ToS52DiagnosticSeverity.Warning, "Unsupported attribute $attr") }
        return S57PortrayalFeature(
            id = portrayalId,
            sourceFeatureId = id,
            objectClassAcronym = objectClass.uppercase(),
            primitive = primitive,
            attributes = normalizedAttributes.mapValues { (name, value) -> value.toPortrayalValue(name) },
            geometry = geometryModel,
            scaleMin = normalizedAttributes["SCAMIN"]?.asIntOrNull(),
            scaleMax = normalizedAttributes["SCAMAX"]?.asIntOrNull()
        )
    }

    private fun S57Feature.splitId(index: Int): Long = id * 1000L + index.toLong() + 1L
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
            diagnostics += S57ToS52Diagnostic(featureId, S57ToS52DiagnosticKind.Geometry, S57ToS52DiagnosticSeverity.Warning, "Polygon has no valid outer ring")
            null
        } else {
            S57PortrayalGeometry.Polygon(
                outer = outer.map { S57PortrayalCoordinate(it.lon, it.lat) },
                holes = rings.drop(1).filter { it.size >= 3 }.map { ring -> ring.map { S57PortrayalCoordinate(it.lon, it.lat) } }
            )
        }
    }
    is S57Geometry.MultiPolygon -> null
}

private fun S57Value.toPortrayalValue(attributeName: String? = null): S57PortrayalValue = when (this) {
    S57Value.Empty -> S57PortrayalValue.Empty()
    is S57Value.Text -> value.textToPortrayalValue(attributeName)
    is S57Value.Integer -> when (attributeName.valueKind()) {
        S57AttributeValueKind.Decimal -> S57PortrayalValue.Decimal(value.toDouble())
        S57AttributeValueKind.EnumerationList -> S57PortrayalValue.ListValue(listOf(S57PortrayalValue.Integer(value)))
        else -> S57PortrayalValue.Integer(value)
    }
    is S57Value.Decimal -> when (attributeName.valueKind()) {
        S57AttributeValueKind.Integer,
        S57AttributeValueKind.Enumeration -> S57PortrayalValue.Integer(value.toInt())
        S57AttributeValueKind.EnumerationList -> S57PortrayalValue.ListValue(listOf(S57PortrayalValue.Integer(value.toInt())))
        else -> S57PortrayalValue.Decimal(value)
    }
    is S57Value.ListValue -> S57PortrayalValue.ListValue(values.map { it.toPortrayalListElement(attributeName) })
}

private fun S57Value.toPortrayalListElement(attributeName: String?): S57PortrayalValue = when (this) {
    S57Value.Empty -> S57PortrayalValue.Empty()
    is S57Value.Text -> value.textToPortrayalListElement(attributeName)
    is S57Value.Integer -> if (attributeName.valueKind() == S57AttributeValueKind.Decimal) S57PortrayalValue.Decimal(value.toDouble()) else S57PortrayalValue.Integer(value)
    is S57Value.Decimal -> if (attributeName.valueKind() == S57AttributeValueKind.Decimal) S57PortrayalValue.Decimal(value) else S57PortrayalValue.Integer(value.toInt())
    is S57Value.ListValue -> S57PortrayalValue.ListValue(values.map { it.toPortrayalListElement(attributeName) })
}

private fun String.textToPortrayalValue(attributeName: String?): S57PortrayalValue {
    val trimmed = trim()
    if (trimmed.isEmpty()) return S57PortrayalValue.Text(this)
    return when (attributeName.valueKind()) {
        S57AttributeValueKind.Decimal -> trimmed.toDoubleOrNull()?.let(S57PortrayalValue::Decimal) ?: S57PortrayalValue.Text(this)
        S57AttributeValueKind.Integer -> trimmed.toIntOrNull()?.let(S57PortrayalValue::Integer) ?: S57PortrayalValue.Text(this)
        S57AttributeValueKind.Enumeration -> trimmed.toIntOrNull()?.let(S57PortrayalValue::Integer) ?: S57PortrayalValue.Text(this)
        S57AttributeValueKind.EnumerationList -> {
            val values = splitNumericListTokens().mapNotNull { it.toIntOrNull()?.let(S57PortrayalValue::Integer) }
            if (values.isNotEmpty()) S57PortrayalValue.ListValue(values) else S57PortrayalValue.Text(this)
        }
        S57AttributeValueKind.Text,
        S57AttributeValueKind.Unknown -> S57PortrayalValue.Text(this)
    }
}

private fun String.textToPortrayalListElement(attributeName: String?): S57PortrayalValue {
    val trimmed = trim()
    return when (attributeName.valueKind()) {
        S57AttributeValueKind.Decimal -> trimmed.toDoubleOrNull()?.let(S57PortrayalValue::Decimal) ?: S57PortrayalValue.Text(this)
        else -> trimmed.toIntOrNull()?.let(S57PortrayalValue::Integer) ?: S57PortrayalValue.Text(this)
    }
}

private fun String?.valueKind(): S57AttributeValueKind = ATTRIBUTE_VALUE_KINDS[this?.uppercase()] ?: S57AttributeValueKind.Unknown

private fun String.splitNumericListTokens(): List<String> =
    split(',', ';', '|', '\u001f', '\u001e').map { it.trim() }.filter { it.isNotEmpty() }

private fun S57Value.splitListValues(): List<S57Value> = when (this) {
    is S57Value.ListValue -> values
    is S57Value.Text -> value.splitNumericListTokens().map(S57Value::Text)
    else -> listOf(this)
}

private fun S57Value.asIntOrNull(): Int? = when (this) {
    is S57Value.Integer -> value
    is S57Value.Decimal -> value.toInt()
    is S57Value.Text -> value.toIntOrNull()
    is S57Value.ListValue -> values.firstOrNull()?.asIntOrNull()
    S57Value.Empty -> null
}

private enum class S57AttributeValueKind {
    Integer,
    Decimal,
    Text,
    Enumeration,
    EnumerationList,
    Unknown
}

private val ATTRIBUTE_VALUE_KINDS: Map<String, S57AttributeValueKind> = mapOf(
    "BURDEP" to S57AttributeValueKind.Decimal,
    "CONRAD" to S57AttributeValueKind.Decimal,
    "DRVAL1" to S57AttributeValueKind.Decimal,
    "DRVAL2" to S57AttributeValueKind.Decimal,
    "ELEVAT" to S57AttributeValueKind.Decimal,
    "ESTRNG" to S57AttributeValueKind.Decimal,
    "HEIGHT" to S57AttributeValueKind.Decimal,
    "HORACC" to S57AttributeValueKind.Decimal,
    "HORCLR" to S57AttributeValueKind.Decimal,
    "HORLEN" to S57AttributeValueKind.Decimal,
    "HORWID" to S57AttributeValueKind.Decimal,
    "ORIENT" to S57AttributeValueKind.Decimal,
    "SECTR1" to S57AttributeValueKind.Decimal,
    "SECTR2" to S57AttributeValueKind.Decimal,
    "SIGPER" to S57AttributeValueKind.Decimal,
    "SOUACC" to S57AttributeValueKind.Decimal,
    "SURATH" to S57AttributeValueKind.Decimal,
    "TS_TSP" to S57AttributeValueKind.Decimal,
    "TS_TSV" to S57AttributeValueKind.Decimal,
    "T_ACWL" to S57AttributeValueKind.Decimal,
    "VALACM" to S57AttributeValueKind.Decimal,
    "VALDCO" to S57AttributeValueKind.Decimal,
    "VALLMA" to S57AttributeValueKind.Decimal,
    "VALMAG" to S57AttributeValueKind.Decimal,
    "VALMXR" to S57AttributeValueKind.Decimal,
    "VALNMR" to S57AttributeValueKind.Decimal,
    "VALSOU" to S57AttributeValueKind.Decimal,
    "VERACC" to S57AttributeValueKind.Decimal,
    "VERCLR" to S57AttributeValueKind.Decimal,
    "VERCCL" to S57AttributeValueKind.Decimal,
    "VERCSA" to S57AttributeValueKind.Decimal,
    "VERLEN" to S57AttributeValueKind.Decimal,
    "LIFCAP" to S57AttributeValueKind.Integer,
    "MLTYLT" to S57AttributeValueKind.Integer,
    "RYRMGV" to S57AttributeValueKind.Integer,
    "SCAMAX" to S57AttributeValueKind.Integer,
    "SCAMIN" to S57AttributeValueKind.Integer,
    "SIGFRQ" to S57AttributeValueKind.Integer,
    "BCNSHP" to S57AttributeValueKind.Enumeration,
    "BOYSHP" to S57AttributeValueKind.Enumeration,
    "CATLAM" to S57AttributeValueKind.Enumeration,
    "CATOBS" to S57AttributeValueKind.Enumeration,
    "CATWRK" to S57AttributeValueKind.Enumeration,
    "COLPAT" to S57AttributeValueKind.Enumeration,
    "EXPSOU" to S57AttributeValueKind.Enumeration,
    "LITCHR" to S57AttributeValueKind.Enumeration,
    "TOPSHP" to S57AttributeValueKind.Enumeration,
    "VERDAT" to S57AttributeValueKind.Enumeration,
    "WATLEV" to S57AttributeValueKind.Enumeration,
    "CATREA" to S57AttributeValueKind.EnumerationList,
    "COLOUR" to S57AttributeValueKind.EnumerationList,
    "COMCHA" to S57AttributeValueKind.EnumerationList,
    "FUNCTN" to S57AttributeValueKind.EnumerationList,
    "NATCON" to S57AttributeValueKind.EnumerationList,
    "PRODCT" to S57AttributeValueKind.EnumerationList,
    "QUASOU" to S57AttributeValueKind.EnumerationList,
    "RESTRN" to S57AttributeValueKind.EnumerationList,
    "STATUS" to S57AttributeValueKind.EnumerationList,
    "TECSOU" to S57AttributeValueKind.EnumerationList
)

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
    val unsupportedObjectClassCount: Int get() = diagnostics.count { it.kind == S57ToS52DiagnosticKind.UnsupportedObjectClass }
    val unsupportedAttributeCount: Int get() = diagnostics.count { it.kind == S57ToS52DiagnosticKind.UnsupportedAttribute }
}

data class S57PortrayalFeature(
    val id: Long,
    val sourceFeatureId: Long = id,
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
    val kind: S57ToS52DiagnosticKind = S57ToS52DiagnosticKind.General,
    val severity: S57ToS52DiagnosticSeverity,
    val message: String
)

enum class S57ToS52DiagnosticKind {
    General,
    Geometry,
    Attribute,
    UnsupportedObjectClass,
    UnsupportedAttribute
}

enum class S57ToS52DiagnosticSeverity {
    Info,
    Warning
}
