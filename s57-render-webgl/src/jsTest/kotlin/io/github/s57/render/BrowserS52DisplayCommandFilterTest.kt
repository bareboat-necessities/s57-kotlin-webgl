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

    private fun feature(
        id: Long = 1L,
        objectClass: String = "BOYLAT",
        attributes: Map<String, S57Value>
    ): S57Feature = S57Feature(
        id = id,
        objectClass = objectClass,
        attributes = attributes,
        geometry = S57Geometry.Point(GeoPoint(-73.5, 40.5))
    )
}
