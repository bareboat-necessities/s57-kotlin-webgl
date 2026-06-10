package io.github.s57.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderPipelineDiagnosticsTest {
    @Test
    fun aggregatesAndExportsStructuredDiagnostics() {
        val report = RenderPipelineDiagnosticReport(
            listOf(
                RenderPipelineDiagnostic(
                    stage = RenderPipelineStage.S52Color,
                    severity = RenderPipelineSeverity.Warning,
                    code = "missing-color-token",
                    message = "Missing color token DEPVS",
                    source = RenderPipelineSource(
                        cellId = "US5TEST",
                        featureId = 7,
                        objectClass = "DEPARE",
                        primitive = "Area",
                        geometryType = "Polygon",
                        attributes = mapOf("DRVAL1" to "3")
                    ),
                    metadata = mapOf("palette" to "DayBright", "fallbackRgb" to "#ff00ff")
                ),
                RenderPipelineDiagnostic(
                    stage = RenderPipelineStage.Adapter,
                    severity = RenderPipelineSeverity.Error,
                    code = "unsupported-geometry",
                    message = "MultiPolygon is not adapted yet",
                    source = RenderPipelineSource(cellId = "US5TEST", objectClass = "LNDARE")
                )
            )
        )

        assertEquals(2, report.diagnostics.size)
        assertEquals(1, report.errorCount)
        assertEquals(mapOf("adapter" to 1, "s52-color" to 1), report.countsByStage())
        assertEquals(mapOf("DEPARE" to 1, "LNDARE" to 1), report.countsByObjectClass())

        val plain = report.toPlainText()
        assertTrue("missing-color-token" in plain)
        assertTrue("object=DEPARE" in plain)

        val json = report.toJson()
        assertTrue("\"countsByStage\":{\"adapter\":1,\"s52-color\":1}" in json)
        assertTrue("\"cellId\":\"US5TEST\"" in json)
        assertTrue("\"attributes\":{\"DRVAL1\":\"3\"}" in json)
        assertTrue("\"fallbackRgb\":\"#ff00ff\"" in json)
    }

    @Test
    fun phase16CountersCreatePipelineDiagnosticsForBlockedAndDegradedStages() {
        val counters = Phase16Counters(
            rawFeatures = 5,
            rawVectors = 3,
            decodedFeatures = 5,
            hasBounds = true,
            geometryDiagnostics = 2,
            indexedFeatures = 5,
            queriedFeatures = 5,
            adaptedFeatures = 0,
            projectedFeatures = 0,
            adapterDiagnostics = 4,
            s52 = S52RenderSummary(
                diagnosticCount = 3,
                unsupportedObjectClassCount = 1,
                unsupportedAttributeCount = 2
            )
        )

        val report = counters.toRenderPipelineDiagnostics(cellId = "US5TEST")

        assertTrue(report.diagnostics.any { it.code == "pipeline-blocked" && it.stage == RenderPipelineStage.Projection })
        assertTrue(report.diagnostics.any { it.code == "geometry-diagnostics-present" })
        assertTrue(report.diagnostics.any { it.code == "adapter-diagnostics-present" })
        assertTrue(report.diagnostics.any { it.code == "unsupported-object-classes" })
        assertTrue(report.toJson().contains("\"failureStage\":\"none\""))
    }

    @Test
    fun artifactReportCreatesFallbackAndGeometryDiagnostics() {
        val artifact = RenderedArtifactReport(
            widthPx = 800,
            heightPx = 600,
            featureCount = 3,
            visibleFeatureCount = 0,
            onscreenFeatureCount = 0,
            offscreenFeatureCount = 3,
            clippedFeatureCount = 0,
            pointFeatureCount = 1,
            lineFeatureCount = 1,
            polygonFeatureCount = 1,
            emptyGeometryCount = 1,
            centerCrosshairHitCount = 0,
            depthMeshVertexCount = 0,
            depthMeshTriangleCount = 0,
            fallbackPlaceholderCount = 2
        )

        val report = artifact.toRenderPipelineDiagnostics(cellId = "US5TEST")

        assertEquals(3, report.warningCount)
        assertTrue(report.diagnostics.any { it.code == "no-onscreen-features" && it.stage == RenderPipelineStage.Viewport })
        assertTrue(report.diagnostics.any { it.code == "empty-geometries" && it.stage == RenderPipelineStage.Geometry })
        assertTrue(report.diagnostics.any { it.code == "fallback-placeholders" && it.stage == RenderPipelineStage.S52Asset })
    }
}
