package io.github.s57.render

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class S52RenderFallbackPolicyTest {
    @Test
    fun usesFallbackWhenS52ClearsCanvasButDrawsNothing() {
        val s52 = S52RenderSummary(
            profile = "OpenCpn",
            encFeatureCount = 4,
            commandCount = 6,
            drawCallCount = 0,
            failureStage = "none"
        )

        assertTrue(s52.needsGeometryFallback(projectedSourceFeatureCount = 4))
    }

    @Test
    fun usesFallbackWhenPortrayalRejectsProjectedFeatures() {
        val s52 = S52RenderSummary(
            profile = "OpenCpn",
            encFeatureCount = 0,
            commandCount = 0,
            drawCallCount = 0,
            failureStage = "portrayal"
        )

        assertTrue(s52.needsGeometryFallback(projectedSourceFeatureCount = 5))
    }

    @Test
    fun doesNotFallbackWhenNoProjectedGeometryExists() {
        val s52 = S52RenderSummary(commandCount = 0, drawCallCount = 0, failureStage = "projection")

        assertFalse(s52.needsGeometryFallback(projectedSourceFeatureCount = 0))
    }

    @Test
    fun doesNotFallbackWhenS52ProducedGpuOutput() {
        val s52 = S52RenderSummary(
            profile = "OpenCpn",
            encFeatureCount = 3,
            commandCount = 4,
            drawCallCount = 4,
            failureStage = "none"
        )

        assertFalse(s52.needsGeometryFallback(projectedSourceFeatureCount = 3))
    }

    @Test
    fun usesFallbackWhenS52IsPointOnlyButSourceHasLineOrAreaGeometry() {
        val s52 = S52RenderSummary(
            profile = "OpenCpn",
            encFeatureCount = 12,
            commandCount = 12,
            drawCallCount = 12,
            symbolCommandCount = 9,
            textCommandCount = 3,
            failureStage = "none"
        )

        assertTrue(s52.needsGeometryFallback(projectedSourceFeatureCount = 12, projectedLinearOrAreaFeatureCount = 4))
    }

    @Test
    fun keepsS52WhenPointOnlyOutputMatchesPointOnlySource() {
        val s52 = S52RenderSummary(
            profile = "OpenCpn",
            encFeatureCount = 4,
            commandCount = 4,
            drawCallCount = 4,
            symbolCommandCount = 4,
            failureStage = "none"
        )

        assertFalse(s52.needsGeometryFallback(projectedSourceFeatureCount = 4, projectedLinearOrAreaFeatureCount = 0))
    }

    @Test
    fun overlaysDecodedGeometryWhenS52ReportsDrawCallsButMayStillLookLikeDots() {
        val s52 = S52RenderSummary(
            profile = "OpenCpn",
            encFeatureCount = 10,
            commandCount = 10,
            drawCallCount = 10,
            lineCommandCount = 2,
            symbolCommandCount = 8,
            failureStage = "none"
        )

        assertTrue(s52.shouldOverlayDecodedGeometry(projectedSourceFeatureCount = 10, projectedLinearOrAreaFeatureCount = 2))
    }

    @Test
    fun doesNotOverlayDecodedGeometryAfterFailedS52BecauseFallbackOwnsTheCanvas() {
        val s52 = S52RenderSummary(commandCount = 0, drawCallCount = 0, failureStage = "portrayal")

        assertFalse(s52.shouldOverlayDecodedGeometry(projectedSourceFeatureCount = 10, projectedLinearOrAreaFeatureCount = 2))
    }

}
