package io.github.s57.render

import io.github.s52.api.S52PortrayalRequest
import io.github.s52.api.S52PortrayalSession
import io.github.s52.catalog.PrimitiveType
import io.github.s52.catalog.S57Attribute
import io.github.s52.catalog.S57AttributeValueKind
import io.github.s52.catalog.S57ObjectClass
import io.github.s52.core.draw.DisplayPrioritySorter
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
        val commands = portrayResiliently(encFeatures, settings, context, diagnostics)
            // The resilient path can concatenate independently portrayed chunks;
            // restore one global S-52 painter order before WebGL draws areas,
            // contours, symbols, and text.
            .sortedWith(DisplayPrioritySorter)
        val rasterCommandCount = commands.count { it.usesRasterPresentationAsset() }
        return BrowserS52PortrayalResult(profile, encFeatures.size, commands, rasterCommandCount, diagnostics, settings)
    }

    private fun S57Feature.toEncFeatures(diagnostics: MutableList<RenderPipelineDiagnostic>): List<EncFeature> {
        val normalizedObject = objectClass.uppercase()
        val sourceGeometry = geometry
        return when {
            normalizedObject == "SOUNDG" && sourceGeometry is S57Geometry.MultiPoint -> splitSoundingEncFeatures(sourceGeometry.points, diagnostics)
            sourceGeometry is S57Geometry.MultiPoint -> splitPointEncFeatures(sourceGeometry.points, diagnostics)
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


    private fun S57Feature.splitPointEncFeatures(points: List<GeoPoint>, diagnostics: MutableList<RenderPipelineDiagnostic>): List<EncFeature> {
        if (points.isEmpty()) {
            diagnostics += s52AdapterDiagnostic(
                featureId = id,
                objectClass = objectClass,
                geometryType = "MultiPoint",
                code = "s52.empty_point_multipoint",
                message = "feature=$id ${objectClass.uppercase()} multipoint is empty"
            )
            return emptyList()
        }
        diagnostics += s52AdapterDiagnostic(
            severity = RenderPipelineSeverity.Info,
            featureId = id,
            objectClass = objectClass,
            geometryType = "MultiPoint",
            code = "s52.split_point_multipoint",
            message = "feature=$id ${objectClass.uppercase()} multipoint split into ${points.size} point features"
        )
        return points.mapIndexedNotNull { index, point ->
            toEncFeature(splitId(index), S57Geometry.Point(point), attributes, diagnostics)
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
        val sourceObjectClassAcronym = objectClass.uppercase()
        val portrayalObjectClassAcronym = sourceObjectClassAcronym.s52CompatibleObjectClassAcronym(primitive)
        val objectClass = s52ObjectClass(portrayalObjectClassAcronym)
        if (objectClass == null) {
            val quietlyUnmodeled = sourceObjectClassAcronym.isKnownUnmodeledObjectClass()
            diagnostics += s52AdapterDiagnostic(
                severity = if (quietlyUnmodeled) RenderPipelineSeverity.Info else RenderPipelineSeverity.Warning,
                featureId = id,
                objectClass = this.objectClass,
                primitive = primitive.name,
                geometryType = sourceGeometry.diagnosticGeometryType(),
                code = if (quietlyUnmodeled) "s52.unmodeled_object_class" else "s52.unsupported_object_class",
                message = if (quietlyUnmodeled) {
                    "feature=$id objectClass=${this.objectClass} is valid S-57 but not modeled by the bundled S-52 catalogue; skipped without debug fallback"
                } else {
                    "feature=$id unsupported objectClass=${this.objectClass}"
                }
            )
            return null
        }
        if (portrayalObjectClassAcronym != sourceObjectClassAcronym) {
            diagnostics += s52AdapterDiagnostic(
                severity = RenderPipelineSeverity.Info,
                featureId = id,
                objectClass = this.objectClass,
                primitive = primitive.name,
                geometryType = sourceGeometry.diagnosticGeometryType(),
                code = "s52.object_class_alias",
                message = "feature=$id ${this.objectClass} portrayed through $portrayalObjectClassAcronym for S-52/OpenCPN compatibility"
            )
        }
        if (!objectClass.supports(primitive)) {
            val quietlyUnmodeled = sourceObjectClassAcronym.isKnownUnmodeledPrimitive(primitive)
            diagnostics += s52AdapterDiagnostic(
                severity = if (quietlyUnmodeled) RenderPipelineSeverity.Info else RenderPipelineSeverity.Warning,
                featureId = id,
                objectClass = objectClass.acronym,
                primitive = primitive.name,
                geometryType = sourceGeometry.diagnosticGeometryType(),
                code = if (quietlyUnmodeled) "s52.unmodeled_primitive" else "s52.unsupported_primitive",
                message = if (quietlyUnmodeled) {
                    "feature=$id objectClass=${objectClass.acronym} primitive=$primitive is valid ENC metadata but not rendered by the bundled S-52 catalogue"
                } else {
                    "feature=$id objectClass=${objectClass.acronym} unsupported primitive=$primitive"
                }
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
                val knownButUnmodeled = name.isKnownS57AttributeMissingFromBundledS52Catalog()
                diagnostics += s52AdapterDiagnostic(
                    severity = if (knownButUnmodeled) RenderPipelineSeverity.Info else RenderPipelineSeverity.Warning,
                    featureId = featureId,
                    objectClass = objectClass,
                    attributes = listOf(name),
                    code = if (knownButUnmodeled) "s52.unmodeled_attribute" else "s52.unsupported_attribute",
                    message = if (knownButUnmodeled) {
                        "feature=$featureId ignored S-57 attribute=$name because the bundled S-52 catalogue has no typed entry for it"
                    } else {
                        "feature=$featureId ignored unsupported attribute=$name"
                    }
                )
            } else {
                pairs += attr to value.rawToS52Value(attr)
            }
        }
        return S57Attributes.of(*pairs.toTypedArray())
    }

    /**
     * Renderable ENC cells may contain object classes/attribute combinations that
     * trigger an OpenCPN conditional-symbology procedure exception in the current
     * S-52 library, for example:
     *
     *   DEPARE CSP received DRGARE
     *   OBSTRN CSP received UWTROC
     *
     * Treating one CSP exception as a whole-frame failure leaves the browser with
     * the blank blue canvas seen in Chrome and in CI snapshots.  The real chart is
     * still usable for portrayal once the single bad feature is isolated.  This
     * bisecting path keeps the normal all-at-once path for healthy cells, then
     * recursively splits only failed chunks and skips individual offending
     * features with diagnostics.
     */
    private fun portrayResiliently(
        encFeatures: List<EncFeature>,
        settings: MarinerSettings,
        context: PortrayalContext,
        diagnostics: MutableList<RenderPipelineDiagnostic>
    ): List<S52DrawCommand> = portrayChunk(encFeatures, settings, context, diagnostics)

    private fun portrayChunk(
        encFeatures: List<EncFeature>,
        settings: MarinerSettings,
        context: PortrayalContext,
        diagnostics: MutableList<RenderPipelineDiagnostic>
    ): List<S52DrawCommand> {
        if (encFeatures.isEmpty()) return emptyList()
        return try {
            profile.createSession().portray(S52PortrayalRequest(encFeatures, settings, context)).commands
        } catch (t: Throwable) {
            if (encFeatures.size == 1) {
                val failed = encFeatures.first()
                diagnostics += failed.toS52CspSkipDiagnostic(t)
                emptyList()
            } else {
                val midpoint = encFeatures.size / 2
                portrayChunk(encFeatures.take(midpoint), settings, context, diagnostics) +
                    portrayChunk(encFeatures.drop(midpoint), settings, context, diagnostics)
            }
        }
    }

    private fun EncFeature.toS52CspSkipDiagnostic(t: Throwable): RenderPipelineDiagnostic = RenderPipelineDiagnostic(
        stage = RenderPipelineStage.S52Portrayal,
        severity = RenderPipelineSeverity.Warning,
        code = "s52.csp_feature_skipped",
        message = "Skipped one ENC feature after S-52 conditional symbology failed: " + t.cleanS52ErrorMessage(),
        source = RenderPipelineSource(
            featureId = id,
            objectClass = objectClass.acronym,
            primitive = primitive.name,
            geometryType = geometry.diagnosticGeometryType()
        ),
        metadata = mapOf(
            "profile" to profile.name,
            "exception" to t.cleanS52ErrorMessage()
        )
    )

    private fun S52DrawCommand.usesRasterPresentationAsset(): Boolean = when (this) {
        is S52DrawCommand.PointSymbol -> session.presLib.symbols.find(symbolName)?.bitmap != null
        is S52DrawCommand.AreaPattern -> session.presLib.patterns.find(patternName)?.bitmap != null
        is S52DrawCommand.LineComplex -> session.presLib.lineStyles.find(lineStyleName)?.bitmap != null
        else -> false
    }
}

internal data class BrowserS52PortrayalResult(
    val profile: BrowserS52RuntimeProfile,
    val featureCount: Int,
    val commands: List<S52DrawCommand>,
    val rasterCommandCount: Int,
    val diagnostics: List<RenderPipelineDiagnostic>,
    val settings: MarinerSettings
) {
    fun toSummary(failureStage: String = "none", drawCallCount: Int = 0): S52RenderSummary = S52RenderSummary(
        profile = profile.name,
        encFeatureCount = featureCount,
        commandCount = commands.size,
        rasterCommandCount = rasterCommandCount,
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
    source = RenderPipelineSource(
        featureId = featureId,
        objectClass = objectClass,
        primitive = primitive,
        geometryType = geometryType,
        attributes = attributes.associateWith { "involved" }
    )
)

private fun S57Geometry.diagnosticGeometryType(): String = when (this) {
    S57Geometry.Empty -> "Empty"
    is S57Geometry.Point -> "Point"
    is S57Geometry.MultiPoint -> "MultiPoint"
    is S57Geometry.LineString -> "LineString"
    is S57Geometry.Polygon -> "Polygon"
    is S57Geometry.MultiPolygon -> "MultiPolygon"
}

private fun EncGeometry.diagnosticGeometryType(): String = when (this) {
    is EncGeometry.Point -> "Point"
    is EncGeometry.MultiPoint -> "MultiPoint"
    is EncGeometry.LineString -> "LineString"
    is EncGeometry.Polygon -> "Polygon"
    else -> "EncGeometry"
}

private fun Throwable.cleanS52ErrorMessage(): String = message?.takeIf { it.isNotBlank() } ?: toString()

private fun GeoPoint.toS52Coordinate(): Coordinate = Coordinate(lon = lon, lat = lat)

private fun S57Value.rawToS52Value(attribute: S57Attribute): S52Value = when (this) {
    S57Value.Empty -> S52Value.Empty
    is S57Value.Text -> value.textToS52Value(attribute)
    is S57Value.Integer -> when (attribute.valueKind) {
        S57AttributeValueKind.Decimal -> S52Value.Decimal(value.toDouble())
        S57AttributeValueKind.EnumerationList -> S52Value.ListValue(listOf(S52Value.Integer(value)))
        else -> S52Value.Integer(value)
    }
    is S57Value.Decimal -> when (attribute.valueKind) {
        S57AttributeValueKind.Integer,
        S57AttributeValueKind.Enumeration -> S52Value.Integer(value.toInt())
        S57AttributeValueKind.EnumerationList -> S52Value.ListValue(listOf(S52Value.Integer(value.toInt())))
        else -> S52Value.Decimal(value)
    }
    is S57Value.ListValue -> S52Value.ListValue(values.map { it.rawListElementToS52Value(attribute) })
}

private fun S57Value.rawListElementToS52Value(attribute: S57Attribute): S52Value = when (this) {
    S57Value.Empty -> S52Value.Empty
    is S57Value.Text -> value.textToS52ListElement(attribute)
    is S57Value.Integer -> if (attribute.valueKind == S57AttributeValueKind.Decimal) S52Value.Decimal(value.toDouble()) else S52Value.Integer(value)
    is S57Value.Decimal -> if (attribute.valueKind == S57AttributeValueKind.Decimal) S52Value.Decimal(value) else S52Value.Integer(value.toInt())
    is S57Value.ListValue -> S52Value.ListValue(values.map { it.rawListElementToS52Value(attribute) })
}

private fun String.textToS52Value(attribute: S57Attribute): S52Value {
    val trimmed = trim()
    if (trimmed.isEmpty()) return S52Value.Text(this)
    return when (attribute.valueKind) {
        S57AttributeValueKind.Decimal -> trimmed.toDoubleOrNull()?.let(S52Value::Decimal) ?: S52Value.Text(this)
        S57AttributeValueKind.Integer -> trimmed.toIntOrNull()?.let(S52Value::Integer) ?: S52Value.Text(this)
        S57AttributeValueKind.Enumeration -> trimmed.toIntOrNull()?.let(S52Value::Integer) ?: S52Value.Text(this)
        S57AttributeValueKind.EnumerationList -> {
            val values = splitNumericListTokens().mapNotNull { it.toIntOrNull()?.let(S52Value::Integer) }
            if (values.isNotEmpty()) S52Value.ListValue(values) else S52Value.Text(this)
        }
        S57AttributeValueKind.Text,
        S57AttributeValueKind.Unknown -> S52Value.Text(this)
    }
}

private fun String.textToS52ListElement(attribute: S57Attribute): S52Value {
    val trimmed = trim()
    return when (attribute.valueKind) {
        S57AttributeValueKind.Decimal -> trimmed.toDoubleOrNull()?.let(S52Value::Decimal) ?: S52Value.Text(this)
        else -> trimmed.toIntOrNull()?.let(S52Value::Integer) ?: S52Value.Text(this)
    }
}

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
    is S57Value.Text -> value.trim().toIntOrNull()
    is S57Value.ListValue -> values.firstOrNull()?.asIntOrNull()
    S57Value.Empty -> null
}

private fun String.s52CompatibleObjectClassAcronym(primitive: PrimitiveType): String = when (uppercase()) {
    // The S-52 v0.5 OpenCPN profile binds these classes to shared CSPs whose
    // guards currently check the shared procedure's nominal class.  Passing the
    // alias avoids whole-frame CSP exceptions while keeping the intended shared
    // portrayal path active.
    "DRGARE" -> "DEPARE"
    "UWTROC" -> "OBSTRN"

    // Valid S-57 classes seen in NOAA ENCs that are absent from the bundled
    // S-52 0.5 catalogue.  Alias them to the nearest supported OpenCPN/S-52
    // portrayal class so the feature is still drawn instead of becoming a red
    // decoded-geometry fallback or a noisy skipped object.
    "ACHBRT" -> "ACHARE"
    "BUAARE" -> "BUILNG"
    "BUISGL" -> if (primitive == PrimitiveType.Point) "BUILNG" else "BUISGL"
    "CBLARE" -> if (primitive == PrimitiveType.Line) "CBLSUB" else "RESARE"
    "CTNARE" -> "RESARE"
    "DRYDOC" -> "DOCARE"
    "HRBFAC" -> if (primitive == PrimitiveType.Point) "MORFAC" else "HRBARE"
    "LNDRGN" -> "LNDARE"
    "PIPARE" -> if (primitive == PrimitiveType.Line) "PIPSOL" else "RESARE"
    "SLOTOP" -> "SLCONS"
    "UNSARE" -> "M_QUAL"
    else -> uppercase()
}

private fun String.isKnownUnmodeledObjectClass(): Boolean {
    val value = uppercase()
    return value.startsWith("OBJL_") || value in setOf(
        // Present in some NOAA ENCs, but not currently in s52-kotlin-webgl 0.5.
        // These are intentionally kept out of the warning counters until the
        // upstream S-52 catalogue grows first-class entries for them.
        "CANBNK",
        // M_NPUB describes nautical-publication metadata/coverage.  It must
        // not be portrayed as LNDARE because many cells encode it as broad
        // polygons that would paint the entire sea as land in snapshots.
        "M_NPUB"
    )
}

private fun String.isKnownUnmodeledPrimitive(primitive: PrimitiveType): Boolean = when (uppercase() to primitive) {
    "ACHBRT" to PrimitiveType.Line,
    "ACHARE" to PrimitiveType.Line,
    "LNDARE" to PrimitiveType.Point,
    "LNDRGN" to PrimitiveType.Point,
    "MAGVAR" to PrimitiveType.Point,
    "SBDARE" to PrimitiveType.Point,
    "SLCONS" to PrimitiveType.Point -> true
    else -> false
}

private fun String.isKnownS57AttributeMissingFromBundledS52Catalog(): Boolean = uppercase() in setOf(
    // These are valid S-57 attributes observed in NOAA cells but missing from
    // the S-52 0.5.4 typed catalogue.  They should not drown CI/browser logs as
    // "unsupported" warnings.  When the upstream catalogue adds them this
    // list can be removed and values will automatically flow to S-52 CSPs.
    "CATAIR",
    "CATLND",
    "CATSEA",
    "CATSIL",
    "CATSLC",
    "CATSLO",
    "CATSPM",
    "NATSUR",
    "NATQUA",
    "TRAFIC"
)

private fun s52ObjectClass(acronym: String): S57ObjectClass? = S57ObjectClass.fromAcronym(acronym)

private fun s52Attribute(acronym: String): S57Attribute? = S57Attribute.fromAcronym(acronym)

private fun browserS52Settings(
    paletteName: String,
    scaleDenominator: Double
): MarinerSettings = MarinerSettings(
    displayCategory = DisplayCategory.Other,
    palette = paletteName.toBrowserS52Palette(),
    scale = scaleDenominator,
    symbolStyle = SymbolStyle.PaperChart,
    boundaryStyle = BoundaryStyle.Symbolized,
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
