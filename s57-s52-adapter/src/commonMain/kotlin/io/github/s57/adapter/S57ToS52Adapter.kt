package io.github.s57.adapter

import io.github.s52.catalog.PrimitiveType
import io.github.s52.catalog.S57Attribute
import io.github.s52.catalog.S57ObjectClass
import io.github.s52.core.draw.S52DrawCommand
import io.github.s52.core.draw.S52DrawCommandTranscript
import io.github.s52.core.engine.S52PortrayalEngine
import io.github.s52.core.geometry.Coordinate
import io.github.s52.core.geometry.EncGeometry
import io.github.s52.core.model.EncFeature
import io.github.s52.core.model.S57Attributes
import io.github.s52.core.settings.MarinerSettings
import io.github.s52.core.settings.PortrayalContext
import io.github.s57.core.GeoPoint
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import io.github.s57.core.S57Value as SourceS57Value
import io.github.s52.core.model.S57Value as TargetS57Value

/**
 * Phase 6 bridge from decoded S-57 features into the s52-kotlin-webgl core model.
 *
 * This module intentionally performs only data adaptation. It does not own chart
 * quilting, pan/zoom UI, AIS, NMEA, or WebGL draw submission. A larger chartplotter
 * can reuse this adapter before handing commands to the S-52 renderer.
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

    fun portray(
        features: List<S57Feature>,
        engine: S52PortrayalEngine,
        settings: MarinerSettings,
        context: PortrayalContext
    ): S57ToS52PortrayalResult {
        val adapted = adaptFeatures(features)
        val commands = engine.portray(adapted.features, settings, context)
        return S57ToS52PortrayalResult(
            adapted = adapted,
            commands = commands,
            transcript = S52DrawCommandTranscript.serialize(commands)
        )
    }

    private fun adaptFeature(feature: S57Feature, diagnostics: MutableList<S57ToS52Diagnostic>): EncFeature? {
        val primitive = feature.geometry.toS52Primitive()
        if (primitive == null) {
            diagnostics += S57ToS52Diagnostic(feature.id, S57ToS52DiagnosticSeverity.Warning, "Feature has no renderable geometry")
            return null
        }

        val objectClass = S57ObjectClass.fromAcronym(feature.objectClass)
        if (objectClass == null) {
            diagnostics += S57ToS52Diagnostic(feature.id, S57ToS52DiagnosticSeverity.Warning, "Unknown S-52 object class '${feature.objectClass}'")
            return null
        }
        if (!objectClass.supports(primitive)) {
            diagnostics += S57ToS52Diagnostic(
                feature.id,
                S57ToS52DiagnosticSeverity.Warning,
                "Object class ${objectClass.acronym} does not support primitive $primitive"
            )
            return null
        }

        val geometry = feature.geometry.toS52Geometry(feature.id, diagnostics) ?: return null
        val attributes = feature.attributes.toS52Attributes(feature.id, diagnostics)
        return EncFeature(
            id = feature.id,
            objectClass = objectClass,
            primitive = primitive,
            attributes = attributes,
            geometry = geometry,
            scaleMin = feature.attributes["SCAMIN"]?.asIntOrNull(),
            scaleMax = feature.attributes["SCAMAX"]?.asIntOrNull()
        )
    }

    private fun Map<String, SourceS57Value>.toS52Attributes(
        featureId: Long,
        diagnostics: MutableList<S57ToS52Diagnostic>
    ): S57Attributes {
        val pairs = mutableListOf<Pair<S57Attribute, TargetS57Value>>()
        for ((acronym, value) in this) {
            val attribute = S57Attribute.fromAcronym(acronym)
            if (attribute == null) {
                diagnostics += S57ToS52Diagnostic(featureId, S57ToS52DiagnosticSeverity.Info, "Unknown S-52 attribute '$acronym' ignored")
            } else {
                pairs += attribute to value.toS52Value()
            }
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
                diagnostics += S57ToS52Diagnostic(featureId, S57ToS52DiagnosticSeverity.Info, "S-52 core model has no MultiPolygon; first polygon used")
                first.toS52Geometry(featureId, diagnostics)
            }
        }
    }

    private fun GeoPoint.toS52Coordinate(): Coordinate = Coordinate(lon = lon, lat = lat)

    private fun SourceS57Value.toS52Value(): TargetS57Value = when (this) {
        SourceS57Value.Empty -> TargetS57Value.Empty
        is SourceS57Value.Text -> TargetS57Value.Text(value)
        is SourceS57Value.Integer -> TargetS57Value.Integer(value)
        is SourceS57Value.Decimal -> TargetS57Value.Decimal(value)
        is SourceS57Value.ListValue -> TargetS57Value.ListValue(values.map { it.toS52Value() })
    }

    private fun SourceS57Value.asIntOrNull(): Int? = when (this) {
        is SourceS57Value.Integer -> value
        is SourceS57Value.Decimal -> value.toInt()
        is SourceS57Value.Text -> value.toIntOrNull()
        is SourceS57Value.ListValue -> values.firstOrNull()?.asIntOrNull()
        SourceS57Value.Empty -> null
    }
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

data class S57ToS52PortrayalResult(
    val adapted: S57ToS52Result,
    val commands: List<S52DrawCommand>,
    val transcript: String
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
