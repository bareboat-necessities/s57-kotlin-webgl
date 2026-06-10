package io.github.s57.render

import io.github.s52.api.S52PortrayalRequest
import io.github.s52.api.S52PortrayalSession
import io.github.s52.catalog.PrimitiveType
import io.github.s52.catalog.S57Attribute
import io.github.s52.catalog.S57ObjectClass
import io.github.s52.core.draw.S52DrawCommand
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

internal enum class BrowserS52RuntimeProfile {
    OpenCpn,
    S52LibCompat
}

private fun BrowserS52RuntimeProfile.createSession(): S52PortrayalSession = when (this) {
    BrowserS52RuntimeProfile.OpenCpn -> S52PortrayalSession.openCpn(failOnStaticCompletenessErrors = false)
    BrowserS52RuntimeProfile.S52LibCompat -> S52PortrayalSession.s52LibCompat(failOnStaticCompletenessErrors = false)
}

internal class BrowserS52Bridge(
    private val profile: BrowserS52RuntimeProfile = BrowserS52RuntimeProfile.OpenCpn,
    private val session: S52PortrayalSession = profile.createSession()
) {
    val presLib get() = session.presLib

    fun portray(
        features: List<S57Feature>,
        paletteName: String,
        scaleDenominator: Double
    ): BrowserS52PortrayalResult {
        val diagnostics = mutableListOf<RenderPipelineDiagnostic>()
        val encFeatures = features.flatMap { it.toEncFeatures(diagnostics) }
        val settings = browserS52Settings(paletteName, scaleDenominator)
        val context = PortrayalContext(compilationScale = settings.scale, displayScale = settings.scale)
        val result = session.portray(S52PortrayalRequest(encFeatures, settings, context))
        return BrowserS52PortrayalResult(profile, encFeatures.size, result.commands, diagnostics, settings)
    }

    private fun S57Feature.toEncFeatures(diagnostics: MutableList<RenderPipelineDiagnostic>): List<EncFeature> {
        val normalizedObject = objectClass.uppercase()
        val sourceGeometry = geometry
        return when {
            normalizedObject == "SOUNDG" && sourceGeometry is S57Geometry.MultiPoint -> splitSoundingEncFeatures(sourceGeometry.points, diagnostics)
            sourceGeometry is S57Geometry.MultiPolygon -> splitMultiPolygonEncFeatures(sourceGeometry.polygons, diagnostics)
            else -> listOfNotNull(toEncFeature(id, sourceGeometry, attributes, diagnostics))
        }
    }

    private fun S57Feature.splitMultiPolygonEncFeatures(polygons: List<S57Geometry.Polygon>, diagnostics: MutableList<RenderPipelineDiagnostic>): List<EncFeature> {
        if (polygons.isEmpty()) {
            diagnostics += s52AdapterDiagnostic(
                featureId = id,
                objectClass = objectClass,
                geometryType = "MultiPolygon",
                code = "s52.empty_multipolygon",
                message = "feature=$id multipolygon is empty"
            )
            return emptyList()
        }
        diagnostics += s52AdapterDiagnostic(
            severity = RenderPipelineSeverity.Info,
            featureId = id,
            objectClass = objectClass,
            geometryType = "MultiPolygon",
            code = "s52.split_multipolygon",
            message = "feature=$id multipolygon split into ${polygons.size} features"
        )
        return polygons.mapIndexedNotNull { index, polygon ->
            toEncFeature(splitId(index), polygon, attributes, diagnostics)
        }
    }

    private fun S57Feature.splitSoundingEncFeatures(points: List<GeoPoint>, diagnostics: MutableList<RenderPipelineDiagnostic>): List<EncFeature> {
        if (points.isEmpty()) {
            diagnostics += s52AdapterDiagnostic(
                featureId = id,
                objectClass = objectClass,
                geometryType = "MultiPoint",
                code = "s52.empty_sounding_multipoint",
                message = "feature=$id SOUNDG multipoint is empty"
            )
            return emptyList()
        }
        val valsou = attributes["VALSOU"]?.splitListValues().orEmpty()
        if (valsou.isNotEmpty() && valsou.size != points.size) {
            diagnostics += s52AdapterDiagnostic(
                featureId = id,
                objectClass = objectClass,
                geometryType = "MultiPoint",
                attributes = listOf("VALSOU"),
                code = "s52.sounding_value_count_mismatch",
                message = "feature=$id SOUNDG VALSOU count ${valsou.size} does not match point count ${points.size}"
            )
        }
        return points.mapIndexedNotNull { index, point ->
            val attrs = attributes.toMutableMap()
            val pointDepth = valsou.getOrNull(index) ?: valsou.firstOrNull() ?: attributes["VALSOU"]
            if (pointDepth != null) attrs["VALSOU"] = pointDepth
            toEncFeature(splitId(index), S57Geometry.Point(point), attrs, diagnostics)
        }
    }

    private fun S57Feature.toEncFeature(
        encId: Long,
        sourceGeometry: S57Geometry,
        sourceAttributes: Map<String, S57Value>,
        diagnostics: MutableList<RenderPipelineDiagnostic>
    ): EncFeature? {
        val primitive = sourceGeometry.toS52Primitive()
        if (primitive == null) {
            diagnostics += s52AdapterDiagnostic(
                featureId = id,
                objectClass = objectClass,
                geometryType = sourceGeometry.diagnosticGeometryType(),
                code = "s52.no_renderable_geometry",
                message = "feature=$id no renderable geometry"
            )
            return null
        }
        val objectClass = s52ObjectClass(objectClass)
        if (objectClass == null) {
            diagnostics += s52AdapterDiagnostic(
                featureId = id,
                objectClass = this.objectClass,
                primitive = primitive.name,
                geometryType = sourceGeometry.diagnosticGeometryType(),
                code = "s52.unsupported_object_class",
                message = "feature=$id unsupported objectClass=${this.objectClass}"
            )
            return null
        }
        if (!objectClass.supports(primitive)) {
            diagnostics += s52AdapterDiagnostic(
                featureId = id,
                objectClass = objectClass.acronym,
                primitive = primitive.name,
                geometryType = sourceGeometry.diagnosticGeometryType(),
                code = "s52.unsupported_primitive",
                message = "feature=$id objectClass=${objectClass.acronym} unsupported primitive=$primitive"
            )
            return null
        }
        val geometry = sourceGeometry.toS52Geometry(id, this.objectClass, diagnostics) ?: return null
        return EncFeature(
            id = encId,
            objectClass = objectClass,
            primitive = primitive,
            attributes = sourceAttributes.toS52Attributes(id, this.objectClass, diagnostics),
            geometry = geometry,
            scaleMin = sourceAttributes["SCAMIN"]?.asIntOrNull(),
            scaleMax = sourceAttributes["SCAMAX"]?.asIntOrNull()
        )
    }

    private fun S57Feature.splitId(index: Int): Long = id * 1000L + index.toLong() + 1L

    private fun Map<String, S57Value>.toS52Attributes(featureId: Long, objectClass: String, diagnostics: MutableList<RenderPipelineDiagnostic>): S57Attributes {
        val pairs = mutableListOf<Pair<S57Attribute, S52Value>>()
        for ((rawName, value) in this) {
            val name = rawName.uppercase()
            val attr = s52Attribute(name)
            if (attr == null) {
                diagnostics += s52AdapterDiagnostic(
                    featureId = featureId,
                    objectClass = objectClass,
                    attributes = listOf(name),
                    code = "s52.unsupported_attribute",
                    message = "feature=$featureId ignored unsupported attribute=$name"
                )
            } else {
                pairs += attr to value.rawToS52Value(name)
            }
        }
        return S57Attributes.of(*pairs.toTypedArray())
    }
}

internal data class BrowserS52PortrayalResult(
    val profile: BrowserS52RuntimeProfile,
    val featureCount: Int,
    val commands: List<S52DrawCommand>,
    val diagnostics: List<RenderPipelineDiagnostic>,
    val settings: MarinerSettings
) {
    fun toSummary(failureStage: String = "none", drawCallCount: Int = 0): S52RenderSummary = S52RenderSummary(
        profile = profile.name,
        encFeatureCount = featureCount,
        commandCount = commands.size,
        drawCallCount = drawCallCount,
        areaCommandCount = commands.count { it is S52DrawCommand.AreaFill || it is S52DrawCommand.AreaPattern },
        lineCommandCount = commands.count { it is S52DrawCommand.LineSimple || it is S52DrawCommand.LineComplex },
        symbolCommandCount = commands.count { it is S52DrawCommand.PointSymbol },
        textCommandCount = commands.count { it is S52DrawCommand.Text },
        soundingCommandCount = commands.count { it is S52DrawCommand.Sounding },
        diagnosticCount = diagnostics.size,
        unsupportedObjectClassCount = diagnostics.count { it.code == "s52.unsupported_object_class" },
        unsupportedAttributeCount = diagnostics.count { it.code == "s52.unsupported_attribute" },
        missingSymbolCount = diagnostics.count { it.code == "s52.missing_symbol" },
        missingColorTokenCount = diagnostics.count { it.code == "s52.missing_color_token" },
        fallbackColorCount = diagnostics.count { it.fallbackColor != null },
        failureStage = failureStage,
        diagnostics = diagnostics
    )
}

private fun S57Geometry.toS52Primitive(): PrimitiveType? = when (this) {
    S57Geometry.Empty -> null
    is S57Geometry.Point -> PrimitiveType.Point
    is S57Geometry.MultiPoint -> PrimitiveType.Point
    is S57Geometry.LineString -> PrimitiveType.Line
    is S57Geometry.Polygon -> PrimitiveType.Area
    is S57Geometry.MultiPolygon -> PrimitiveType.Area
}

private fun S57Geometry.toS52Geometry(featureId: Long, objectClass: String?, diagnostics: MutableList<RenderPipelineDiagnostic>): EncGeometry? = when (this) {
    S57Geometry.Empty -> null
    is S57Geometry.Point -> EncGeometry.Point(coordinate.toS52Coordinate())
    is S57Geometry.MultiPoint -> EncGeometry.MultiPoint(points.map { it.toS52Coordinate() })
    is S57Geometry.LineString -> EncGeometry.LineString(points.map { it.toS52Coordinate() })
    is S57Geometry.Polygon -> {
        val outer = rings.firstOrNull().orEmpty()
        if (outer.size < 3) {
            diagnostics += s52AdapterDiagnostic(
                featureId = featureId,
                objectClass = objectClass,
                geometryType = "Polygon",
                code = "s52.invalid_polygon_outer_ring",
                message = "feature=$featureId polygon has no valid outer ring"
            )
            null
        } else {
            EncGeometry.Polygon(
                outer = outer.map { it.toS52Coordinate() },
                holes = rings.drop(1).filter { it.size >= 3 }.map { ring -> ring.map { it.toS52Coordinate() } }
            )
        }
    }
    is S57Geometry.MultiPolygon -> null
}


private fun s52AdapterDiagnostic(
    featureId: Long,
    objectClass: String? = null,
    primitive: String? = null,
    geometryType: String? = null,
    attributes: List<String> = emptyList(),
    severity: RenderPipelineSeverity = RenderPipelineSeverity.Warning,
    code: String,
    message: String
): RenderPipelineDiagnostic = RenderPipelineDiagnostic(
    stage = RenderPipelineStage.Adapter,
    severity = severity,
    code = code,
    message = message,
    featureId = featureId,
    objectClass = objectClass,
    primitive = primitive,
    geometryType = geometryType,
    attributes = attributes
)

private fun S57Geometry.diagnosticGeometryType(): String = when (this) {
    S57Geometry.Empty -> "Empty"
    is S57Geometry.Point -> "Point"
    is S57Geometry.MultiPoint -> "MultiPoint"
    is S57Geometry.LineString -> "LineString"
    is S57Geometry.Polygon -> "Polygon"
    is S57Geometry.MultiPolygon -> "MultiPolygon"
}

private fun GeoPoint.toS52Coordinate(): Coordinate = Coordinate(lon = lon, lat = lat)

private fun S57Value.rawToS52Value(attributeName: String? = null): S52Value = when (this) {
    S57Value.Empty -> S52Value.Empty
    is S57Value.Text -> value.textToS52Value(attributeName)
    is S57Value.Integer -> S52Value.Integer(value)
    is S57Value.Decimal -> S52Value.Decimal(value)
    is S57Value.ListValue -> S52Value.ListValue(values.map { it.rawToS52Value(attributeName) })
}

private fun String.textToS52Value(attributeName: String?): S52Value {
    val trimmed = trim()
    if (attributeName in NUMERIC_DECIMAL_ATTRIBUTES) trimmed.toDoubleOrNull()?.let { return S52Value.Decimal(it) }
    if (attributeName in NUMERIC_INTEGER_ATTRIBUTES) trimmed.toIntOrNull()?.let { return S52Value.Integer(it) }
    return S52Value.Text(this)
}

private fun S57Value.splitListValues(): List<S57Value> = when (this) {
    is S57Value.ListValue -> values
    is S57Value.Text -> value.split(',', ';', '|').mapNotNull { token -> token.trim().takeIf { it.isNotEmpty() }?.let(S57Value::Text) }
    else -> listOf(this)
}

private fun S57Value.asIntOrNull(): Int? = when (this) {
    is S57Value.Integer -> value
    is S57Value.Decimal -> value.toInt()
    is S57Value.Text -> value.trim().toIntOrNull()
    is S57Value.ListValue -> values.firstOrNull()?.asIntOrNull()
    S57Value.Empty -> null
}

private fun s52ObjectClass(acronym: String): S57ObjectClass? = S57ObjectClass.fromAcronym(acronym)

private fun s52Attribute(acronym: String): S57Attribute? = S57Attribute.fromAcronym(acronym)

private val NUMERIC_DECIMAL_ATTRIBUTES = setOf("DRVAL1", "DRVAL2", "HEIGHT", "VALDCO", "VALSOU")
private val NUMERIC_INTEGER_ATTRIBUTES = setOf("CATOBS", "CATLAM", "CATREA", "CATWRK", "COLOUR", "COLPAT", "LITCHR", "SCAMIN", "SCAMAX", "SIGGRP", "WATLEV")

private fun browserS52Settings(
    paletteName: String,
    scaleDenominator: Double
): MarinerSettings = MarinerSettings(
    displayCategory = DisplayCategory.Other,
    palette = paletteName.toBrowserS52Palette(),
    scale = scaleDenominator,
    symbolStyle = SymbolStyle.Simplified,
    boundaryStyle = BoundaryStyle.Plain,
    showText = true,
    showSoundings = true,
    showLightDescriptions = true
)

private fun String.toBrowserS52Palette(): S52Palette = when (trim().lowercase()) {
    "dusk" -> S52Palette.Dusk
    "night", "dark" -> S52Palette.Night
    "dayblackback" -> S52Palette.DayBlackBack
    "daywhiteback" -> S52Palette.DayWhiteBack
    else -> S52Palette.DayBright
}
