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
        val diagnostics = mutableListOf<String>()
        val encFeatures = features.mapNotNull { it.toEncFeature(diagnostics) }
        val settings = browserS52Settings(paletteName, scaleDenominator)
        val context = PortrayalContext(compilationScale = settings.scale, displayScale = settings.scale)
        val result = session.portray(S52PortrayalRequest(encFeatures, settings, context))
        return BrowserS52PortrayalResult(profile, encFeatures.size, result.commands, diagnostics, settings)
    }

    private fun S57Feature.toEncFeature(diagnostics: MutableList<String>): EncFeature? {
        val primitive = geometry.toS52Primitive()
        if (primitive == null) {
            diagnostics += "feature=$id no renderable geometry"
            return null
        }
        val objectClass = s52ObjectClass(objectClass)
        if (objectClass == null) {
            diagnostics += "feature=$id unsupported objectClass=${this.objectClass}"
            return null
        }
        if (!objectClass.supports(primitive)) {
            diagnostics += "feature=$id objectClass=${objectClass.acronym} unsupported primitive=$primitive"
            return null
        }
        val geometry = geometry.toS52Geometry(id, diagnostics) ?: return null
        return EncFeature(
            id = id,
            objectClass = objectClass,
            primitive = primitive,
            attributes = attributes.toS52Attributes(id, diagnostics),
            geometry = geometry,
            scaleMin = attributes["SCAMIN"]?.asIntOrNull(),
            scaleMax = attributes["SCAMAX"]?.asIntOrNull()
        )
    }

    private fun Map<String, S57Value>.toS52Attributes(featureId: Long, diagnostics: MutableList<String>): S57Attributes {
        val pairs = mutableListOf<Pair<S57Attribute, S52Value>>()
        for ((name, value) in this) {
            val attr = s52Attribute(name)
            if (attr == null) {
                diagnostics += "feature=$featureId ignored unsupported attribute=${name.uppercase()}"
            } else {
                pairs += attr to value.rawToS52Value()
            }
        }
        return S57Attributes.of(*pairs.toTypedArray())
    }
}

internal data class BrowserS52PortrayalResult(
    val profile: BrowserS52RuntimeProfile,
    val featureCount: Int,
    val commands: List<S52DrawCommand>,
    val diagnostics: List<String>,
    val settings: MarinerSettings
)

private fun S57Geometry.toS52Primitive(): PrimitiveType? = when (this) {
    S57Geometry.Empty -> null
    is S57Geometry.Point -> PrimitiveType.Point
    is S57Geometry.MultiPoint -> PrimitiveType.Point
    is S57Geometry.LineString -> PrimitiveType.Line
    is S57Geometry.Polygon -> PrimitiveType.Area
    is S57Geometry.MultiPolygon -> PrimitiveType.Area
}

private fun S57Geometry.toS52Geometry(featureId: Long, diagnostics: MutableList<String>): EncGeometry? = when (this) {
    S57Geometry.Empty -> null
    is S57Geometry.Point -> EncGeometry.Point(coordinate.toS52Coordinate())
    is S57Geometry.MultiPoint -> EncGeometry.MultiPoint(points.map { it.toS52Coordinate() })
    is S57Geometry.LineString -> EncGeometry.LineString(points.map { it.toS52Coordinate() })
    is S57Geometry.Polygon -> {
        val outer = rings.firstOrNull().orEmpty()
        if (outer.size < 3) {
            diagnostics += "feature=$featureId polygon has no valid outer ring"
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
            diagnostics += "feature=$featureId multipolygon is empty"
            null
        } else {
            diagnostics += "feature=$featureId multipolygon flattened to first polygon"
            first.toS52Geometry(featureId, diagnostics)
        }
    }
}

private fun GeoPoint.toS52Coordinate(): Coordinate = Coordinate(lon = lon, lat = lat)

private fun S57Value.rawToS52Value(): S52Value = when (this) {
    S57Value.Empty -> S52Value.Empty
    is S57Value.Text -> S52Value.Text(value)
    is S57Value.Integer -> S52Value.Integer(value)
    is S57Value.Decimal -> S52Value.Decimal(value)
    is S57Value.ListValue -> S52Value.ListValue(values.map { it.rawToS52Value() })
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
