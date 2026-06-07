package io.github.s57.adapter

import io.github.s52.api.S52PortrayalRequest
import io.github.s52.api.S52PortrayalSession
import io.github.s52.catalog.PrimitiveType
import io.github.s52.catalog.S57Attribute
import io.github.s52.catalog.S57ObjectClass
import io.github.s52.core.draw.S52DrawCommand
import io.github.s52.core.draw.S52DrawCommandTranscript
import io.github.s52.core.geometry.Coordinate
import io.github.s52.core.geometry.EncGeometry
import io.github.s52.core.model.EncFeature
import io.github.s52.core.model.S57Attributes
import io.github.s52.core.model.S57Value as S52Value
import io.github.s52.core.settings.BoundaryStyle
import io.github.s52.core.settings.DisplayCategory
import io.github.s52.core.settings.MarinerSettings
import io.github.s52.core.settings.PortrayalContext
import io.github.s52.core.settings.S52Palette
import io.github.s52.core.settings.SymbolStyle
import io.github.s57.core.GeoPoint
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import io.github.s57.core.S57Value

/**
 * Real S-57 -> S-52 bridge.
 *
 * This adapter consumes decoded S-57 features from this project and creates the
 * actual `io.github.s52` `EncFeature` values required by the S-52 portrayal
 * engine. It deliberately keeps diagnostics for unmapped/dynamic S-57 content
 * instead of silently replacing it with generic local draw logic.
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
        val encFeatures = features.mapNotNull { feature -> feature.toEncFeature(diagnostics) }
        return S57ToS52Result(encFeatures, diagnostics)
    }

    fun portray(
        features: List<S57Feature>,
        session: S52PortrayalSession = S52PortrayalSession.s52LibCompat(failOnStaticCompletenessErrors = false),
        settings: MarinerSettings = defaultSettings(),
        context: PortrayalContext = PortrayalContext(compilationScale = settings.scale, displayScale = settings.scale)
    ): S57S52PortrayalResult {
        val adapted = adaptFeatures(features)
        val request = S52PortrayalRequest(adapted.features, settings, context)
        val result = session.portray(request)
        return S57S52PortrayalResult(
            features = adapted.features,
            commands = result.commands,
            diagnostics = adapted.diagnostics,
            commandTranscript = S52DrawCommandTranscript.serialize(result.commands)
        )
    }

    fun transcript(result: S57ToS52Result): String = buildString {
        append("features=${result.features.size}")
        for (feature in result.features) append("\n${feature.id}:${feature.objectClass.acronym}:${feature.primitive}:${feature.attributes.asMap().keys.joinToString(",") { it.acronym }}")
        if (result.diagnostics.isNotEmpty()) {
            append("\ndiagnostics=${result.diagnostics.size}")
            result.diagnostics.forEach { append("\n${it.severity}: feature=${it.featureId} ${it.message}") }
        }
    }

    private fun S57Feature.toEncFeature(diagnostics: MutableList<S57ToS52Diagnostic>): EncFeature? {
        val primitive = geometry.toS52Primitive()
        if (primitive == null) {
            diagnostics += S57ToS52Diagnostic(id, S57ToS52DiagnosticSeverity.Warning, "Feature has no renderable geometry")
            return null
        }

        val objectClass = s52ObjectClass(objectClass)
        if (objectClass == null) {
            diagnostics += S57ToS52Diagnostic(id, S57ToS52DiagnosticSeverity.Warning, "Unsupported S-57 object class '${this.objectClass}' for S-52 v0.3.0 catalogue")
            return null
        }
        if (!objectClass.supports(primitive)) {
            diagnostics += S57ToS52Diagnostic(id, S57ToS52DiagnosticSeverity.Warning, "S-57 object class ${objectClass.acronym} does not support primitive $primitive")
            return null
        }

        val geometry = geometry.toS52Geometry(id, diagnostics) ?: return null
        val attrs = attributes.toS52Attributes(id, diagnostics)
        return EncFeature(
            id = id,
            objectClass = objectClass,
            primitive = primitive,
            attributes = attrs,
            geometry = geometry,
            scaleMin = attributes["SCAMIN"]?.asIntOrNull(),
            scaleMax = attributes["SCAMAX"]?.asIntOrNull()
        )
    }

    private fun Map<String, S57Value>.toS52Attributes(
        featureId: Long,
        diagnostics: MutableList<S57ToS52Diagnostic>
    ): S57Attributes {
        val pairs = mutableListOf<Pair<S57Attribute, S52Value>>()
        for ((name, value) in this) {
            val attribute = s52Attribute(name)
            if (attribute == null) {
                diagnostics += S57ToS52Diagnostic(featureId, S57ToS52DiagnosticSeverity.Info, "Ignoring unsupported S-57 attribute '${name.uppercase()}'")
                continue
            }
            pairs += attribute to value.toS52Value()
        }
        return S57Attributes.of(*pairs.toTypedArray())
    }

    private fun S57Geometry.toS52Primitive(): PrimitiveType? = when (this) {
        S57Geometry.Empty -> null
        is S57Geometry.Point -> PrimitiveType.Point
        is S57Geometry.MultiPoint -> PrimitiveType.Point
        is S57Geometry.LineString -> PrimitiveType.Line
        is S57Geometry.Polygon -> PrimitiveType.Area
        is S57Geometry.MultiPolygon -> PrimitiveType.Area
    }

    private fun S57Geometry.toS52Geometry(
        featureId: Long,
        diagnostics: MutableList<S57ToS52Diagnostic>
    ): EncGeometry? = when (this) {
        S57Geometry.Empty -> null
        is S57Geometry.Point -> EncGeometry.Point(coordinate.toS52Coordinate())
        is S57Geometry.MultiPoint -> EncGeometry.MultiPoint(points.map { it.toS52Coordinate() })
        is S57Geometry.LineString -> EncGeometry.LineString(points.map { it.toS52Coordinate() })
        is S57Geometry.Polygon -> {
            val outer = rings.firstOrNull().orEmpty()
            if (outer.size < 3) {
                diagnostics += S57ToS52Diagnostic(featureId, S57ToS52DiagnosticSeverity.Warning, "Polygon has no valid outer ring")
                null
            } else {
                EncGeometry.Polygon(
                    outer = outer.map { it.toS52Coordinate() },
                    holes = rings.drop(1).filter { it.size >= 3 }.map { ring -> ring.map { it.toS52Coordinate() } }
                )
            }
        }
        is S57Geometry.MultiPolygon -> {
            val first = polygons.firstOrNull()
            if (first == null) {
                diagnostics += S57ToS52Diagnostic(featureId, S57ToS52DiagnosticSeverity.Warning, "MultiPolygon is empty")
                null
            } else {
                diagnostics += S57ToS52Diagnostic(featureId, S57ToS52DiagnosticSeverity.Info, "MultiPolygon flattened to first polygon for S-52 v0.3.0 geometry model")
                first.toS52Geometry(featureId, diagnostics)
            }
        }
    }

    private fun GeoPoint.toS52Coordinate(): Coordinate = Coordinate(lon = lon, lat = lat)

    private fun S57Value.toS52Value(): S52Value = when (this) {
        S57Value.Empty -> S52Value.Empty
        is S57Value.Text -> S52Value.Text(value)
        is S57Value.Integer -> S52Value.Integer(value)
        is S57Value.Decimal -> S52Value.Decimal(value)
        is S57Value.ListValue -> S52Value.ListValue(values.map { it.toS52Value() })
    }

    private fun S57Value.asIntOrNull(): Int? = when (this) {
        is S57Value.Integer -> value
        is S57Value.Decimal -> value.toInt()
        is S57Value.Text -> value.toIntOrNull()
        is S57Value.ListValue -> values.firstOrNull()?.asIntOrNull()
        S57Value.Empty -> null
    }

}

private fun s52ObjectClass(acronym: String): S57ObjectClass? {
    val key = acronym.trim()
    return S57ObjectClass.entries.firstOrNull { it.acronym.equals(key, ignoreCase = true) }
}

private fun s52Attribute(acronym: String): S57Attribute? {
    val key = acronym.trim()
    return S57Attribute.entries.firstOrNull { it.acronym.equals(key, ignoreCase = true) }
}

fun defaultSettings(
    paletteName: String = "DayBright",
    scaleDenominator: Double = 50_000.0
): MarinerSettings = MarinerSettings(
    displayCategory = DisplayCategory.Other,
    palette = paletteName.toS52Palette(),
    scale = scaleDenominator,
    symbolStyle = SymbolStyle.Simplified,
    boundaryStyle = BoundaryStyle.Plain,
    showText = true,
    showSoundings = true,
    showLightDescriptions = true
)

fun String.toS52Palette(): S52Palette = when (trim().lowercase()) {
    "dusk" -> S52Palette.Dusk
    "night", "dark" -> S52Palette.Night
    "dayblackback" -> S52Palette.DayBlackBack
    "daywhiteback" -> S52Palette.DayWhiteBack
    else -> S52Palette.DayBright
}

data class S52AdapterFeature(
    val id: Long,
    val objectClassAcronym: String,
    val attributes: List<String>,
    val geometryType: String
)

data class S57ToS52Result(
    val features: List<EncFeature>,
    val diagnostics: List<S57ToS52Diagnostic>
) {
    val skippedCount: Int get() = diagnostics.count { it.severity == S57ToS52DiagnosticSeverity.Warning }
}

data class S57S52PortrayalResult(
    val features: List<EncFeature>,
    val commands: List<S52DrawCommand>,
    val diagnostics: List<S57ToS52Diagnostic>,
    val commandTranscript: String
)

data class S57ToS52Diagnostic(
    val featureId: Long,
    val severity: S57ToS52DiagnosticSeverity,
    val message: String
)

enum class S57ToS52DiagnosticSeverity {
    Info,
    Warning
}
