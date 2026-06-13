package io.github.s57.render

import io.github.s52.core.draw.S52DrawCommand
import io.github.s52.core.geometry.Coordinate
import io.github.s52.core.geometry.EncGeometry
import io.github.s52.core.instruction.InstructionKind
import io.github.s52.core.settings.DisplayCategory
import io.github.s52.render.webgl.RenderViewport
import io.github.s57.core.GeoPoint
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import io.github.s57.core.S57Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrowserS52DisplayCommandFilterTest {
    private val presLib = BrowserS52Bridge().presLib
    private val viewport = RenderViewport(west = -74.0, south = 40.0, east = -73.0, north = 41.0)

    @Test
    fun resolvesTePrintfObjectNameInsteadOfRenderingPrintfPlaceholder() {
        val plan = buildBrowserS52DisplayCommandPlan(
            commands = listOf(textCommand(textExpression = "%s", rawArgs = listOf("%s", "OBJNAM"))),
            sourceFeatures = listOf(feature(attributes = mapOf("OBJNAM" to S57Value.Text("North Channel Buoy")))),
            presLib = presLib,
            viewport = viewport,
            widthPx = 800,
            heightPx = 600,
            scaleDenominator = 40_000.0
        )

        val label = (plan.textCommands.single() as S52DrawCommand.Text).textExpression
        assertEquals("North Channel Buoy", label)
    }

    @Test
    fun suppressesUnresolvedAttributeAcronymText() {
        val plan = buildBrowserS52DisplayCommandPlan(
            commands = listOf(textCommand(textExpression = "OBJNAM", rawArgs = listOf("OBJNAM"))),
            sourceFeatures = listOf(feature(attributes = emptyMap())),
            presLib = presLib,
            viewport = viewport,
            widthPx = 800,
            heightPx = 600,
            scaleDenominator = 40_000.0
        )

        assertEquals(emptyList(), plan.textCommands)
    }

    @Test
    fun buildsLightDescriptionFromLightAttributesWhenLightTextIsUnresolved() {
        val plan = buildBrowserS52DisplayCommandPlan(
            commands = listOf(textCommand(featureId = 7L, textExpression = "LIGHTS", rawArgs = listOf("LIGHTS"))),
            sourceFeatures = listOf(
                feature(
                    id = 7L,
                    objectClass = "LIGHTS",
                    attributes = mapOf(
                        "LITCHR" to S57Value.Integer(2),
                        "COLOUR" to S57Value.ListValue(listOf(S57Value.Integer(3))),
                        "SIGGRP" to S57Value.Text("(2)"),
                        "SIGPER" to S57Value.Decimal(4.0)
                    )
                )
            ),
            presLib = presLib,
            viewport = viewport,
            widthPx = 800,
            heightPx = 600,
            scaleDenominator = 40_000.0
        )

        val label = (plan.textCommands.single() as S52DrawCommand.Text).textExpression
        assertEquals("Fl R (2) 4s", label)
    }


    @Test
    fun suppressesRasterBackedAreaPatternsWithoutChangingAreaFill() {
        val rasterPattern = presLib.patterns.all().first { it.bitmap != null }
        val plan = buildBrowserS52DisplayCommandPlan(
            commands = listOf(
                areaFillCommand(featureId = 3L, colorToken = "DEPVS"),
                areaPatternCommand(featureId = 3L, patternName = rasterPattern.name)
            ),
            sourceFeatures = listOf(feature(id = 3L, objectClass = "DEPARE", attributes = emptyMap(), geometry = s57Polygon())),
            presLib = presLib,
            viewport = viewport,
            widthPx = 800,
            heightPx = 600,
            scaleDenominator = 40_000.0
        )

        assertEquals(1, plan.suppressedRasterAreaPatternCount)
        assertEquals(1, plan.commands.size)
        assertEquals("DEPVS", (plan.commands.single() as S52DrawCommand.AreaFill).colorToken)
    }

    @Test
    fun doesNotAddSyntheticLandFillBehindPatternOnlyLandRegion() {
        val plan = buildBrowserS52DisplayCommandPlan(
            commands = listOf(areaPatternCommand(featureId = 9L, patternName = "VECTOR_ONLY_PATTERN")),
            sourceFeatures = listOf(feature(id = 9L, objectClass = "LNDRGN", attributes = emptyMap(), geometry = s57Polygon())),
            presLib = presLib,
            viewport = viewport,
            widthPx = 800,
            heightPx = 600,
            scaleDenominator = 40_000.0
        )

        assertEquals(1, plan.commands.size)
        assertTrue(plan.commands.single() is S52DrawCommand.AreaPattern)
    }


    @Test
    fun skipsNauticalPublicationAreasInsteadOfAliasingThemToLand() {
        val result = BrowserS52Bridge().portray(
            features = listOf(feature(id = 42L, objectClass = "M_NPUB", attributes = emptyMap(), geometry = s57Polygon())),
            paletteName = "DAY",
            scaleDenominator = 40_000.0
        )

        assertEquals(0, result.featureCount)
        assertTrue(result.commands.none { command -> command is S52DrawCommand.AreaFill && command.colorToken.uppercase() == "LANDA" })
        assertTrue(result.diagnostics.any { diagnostic -> diagnostic.code == "s52.unmodeled_object_class" })
    }

    private fun textCommand(
        featureId: Long = 1L,
        textExpression: String,
        rawArgs: List<String>
    ): S52DrawCommand.Text = S52DrawCommand.Text(
        featureId = featureId,
        geometry = EncGeometry.Point(Coordinate(-73.5, 40.5)),
        textExpression = textExpression,
        rawArgs = rawArgs,
        textKind = InstructionKind.TE,
        colorToken = "CHBLK",
        priority = 1,
        viewingGroup = 1,
        category = DisplayCategory.Standard,
        overRadar = true
    )

    private fun areaFillCommand(
        featureId: Long = 1L,
        colorToken: String
    ): S52DrawCommand.AreaFill = S52DrawCommand.AreaFill(
        featureId = featureId,
        geometry = encPolygon(),
        colorToken = colorToken,
        priority = 1,
        viewingGroup = 1,
        category = DisplayCategory.Standard,
        overRadar = true
    )


    private fun areaPatternCommand(
        featureId: Long = 1L,
        patternName: String
    ): S52DrawCommand.AreaPattern = S52DrawCommand.AreaPattern(
        featureId = featureId,
        geometry = encPolygon(),
        patternName = patternName,
        priority = 1,
        viewingGroup = 1,
        category = DisplayCategory.Standard,
        overRadar = true
    )

    private fun encPolygon(): EncGeometry.Polygon = EncGeometry.Polygon(
        outer = listOf(
            Coordinate(-73.8, 40.2),
            Coordinate(-73.2, 40.2),
            Coordinate(-73.2, 40.8),
            Coordinate(-73.8, 40.8),
            Coordinate(-73.8, 40.2)
        )
    )

    private fun s57Polygon(): S57Geometry.Polygon = S57Geometry.Polygon(
        rings = listOf(
            listOf(
                GeoPoint(-73.8, 40.2),
                GeoPoint(-73.2, 40.2),
                GeoPoint(-73.2, 40.8),
                GeoPoint(-73.8, 40.8),
                GeoPoint(-73.8, 40.2)
            )
        )
    )

    private fun feature(
        id: Long = 1L,
        objectClass: String = "BOYLAT",
        attributes: Map<String, S57Value>,
        geometry: S57Geometry = S57Geometry.Point(GeoPoint(-73.5, 40.5))
    ): S57Feature = S57Feature(
        id = id,
        objectClass = objectClass,
        attributes = attributes,
        geometry = geometry
    )
}
