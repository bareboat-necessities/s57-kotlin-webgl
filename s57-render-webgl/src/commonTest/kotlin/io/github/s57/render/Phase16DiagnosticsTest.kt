package io.github.s57.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Phase16DiagnosticsTest {
    @Test
    fun reportsRawDecodeStageWhenNothingWasDecoded() {
        val counters = Phase16Counters()
        assertEquals("s57-raw-decode", counters.stage())
        assertTrue(counters.toPlainText().contains("rawFeatures=0"))
    }

    @Test
    fun reportsBoundsStageWhenCellHasNoBounds() {
        val counters = Phase16Counters(
            rawFeatures = 3,
            rawVectors = 2,
            decodedFeatures = 3,
            hasBounds = false
        )
        assertEquals("s57-bounds", counters.stage())
    }

    @Test
    fun reportsProjectionStageWhenNoProjectedFeaturesExist() {
        val counters = Phase16Counters(
            rawFeatures = 3,
            decodedFeatures = 3,
            hasBounds = true,
            indexedFeatures = 3,
            queriedFeatures = 3,
            adaptedFeatures = 3,
            projectedFeatures = 0
        )
        assertEquals("projection", counters.stage())
    }

    @Test
    fun reportsS52StageWhenPortrayalFailed() {
        val counters = Phase16Counters(
            rawFeatures = 4,
            decodedFeatures = 4,
            hasBounds = true,
            indexedFeatures = 4,
            queriedFeatures = 4,
            adaptedFeatures = 4,
            projectedFeatures = 4,
            visibleFeatures = 4,
            s52 = S52RenderSummary(encFeatureCount = 4, commandCount = 0, failureStage = "portrayal")
        )
        assertEquals("portrayal", counters.stage())
    }

    @Test
    fun reportsNoneWhenDecodedGeometryFallbackRecoveredFromS52Failure() {
        val fallbackDiagnostic = RenderPipelineDiagnostic(
            stage = RenderPipelineStage.WebGl,
            severity = RenderPipelineSeverity.Warning,
            code = "s52.geometry_fallback",
            message = "S-52 WebGL render failed: WebGL2 is not available"
        )
        val counters = Phase16Counters(
            rawFeatures = 4,
            decodedFeatures = 4,
            hasBounds = true,
            indexedFeatures = 4,
            queriedFeatures = 4,
            adaptedFeatures = 4,
            projectedFeatures = 4,
            visibleFeatures = 4,
            s52 = S52RenderSummary(
                encFeatureCount = 4,
                commandCount = 0,
                failureStage = "webgl2",
                diagnosticCount = 1,
                diagnostics = listOf(fallbackDiagnostic)
            )
        )

        assertEquals("none", counters.stage())
    }

    @Test
    fun reportsNoneWhenPipelineHasVisibleFeaturesAndS52Commands() {
        val counters = Phase16Counters(
            rawFeatures = 4,
            rawVectors = 3,
            decodedFeatures = 4,
            hasBounds = true,
            indexedFeatures = 4,
            queriedFeatures = 4,
            adaptedFeatures = 4,
            projectedFeatures = 4,
            visibleFeatures = 4,
            s52 = S52RenderSummary(encFeatureCount = 4, commandCount = 8, drawCallCount = 8)
        )
        assertEquals("none", counters.stage())
        assertTrue(counters.toPlainText().contains("s52Commands=8"))
    }
}
