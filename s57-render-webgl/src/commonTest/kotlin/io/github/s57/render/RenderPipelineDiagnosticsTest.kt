package io.github.s57.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderPipelineDiagnosticsTest {
    @Test
    fun serializesDiagnosticWithFeatureAndAttributeContext() {
        val diagnostic = RenderPipelineDiagnostic(
            stage = RenderPipelineStage.Adapter,
            severity = RenderPipelineSeverity.Warning,
            code = "s52.unsupported_attribute",
            message = "feature=42 ignored unsupported attribute=FOO\nnext line",
            cellId = "US5TEST",
            featureId = 42,
            objectClass = "LIGHTS",
            primitive = "Point",
            geometryType = "Point",
            attributes = listOf("FOO")
        )

        val plain = diagnostic.toPlainText()
        val json = diagnostic.toJson()

        assertTrue("stage=adapter" in plain)
        assertTrue("feature=42" in plain)
        assertTrue("attributes=FOO" in plain)
        assertTrue("\"stage\":\"adapter\"" in json)
        assertTrue("\"featureId\":42" in json)
        assertTrue("\"attributes\":[\"FOO\"]" in json)
        assertTrue("\\nnext line" in json)
    }

    @Test
    fun reportAggregatesBySeverityStageCodeAndObjectClass() {
        val report = RenderPipelineDiagnosticReport(
            listOf(
                RenderPipelineDiagnostic(RenderPipelineStage.Adapter, RenderPipelineSeverity.Warning, "s52.unsupported_attribute", "bad attr", objectClass = "LIGHTS"),
                RenderPipelineDiagnostic(RenderPipelineStage.S52WebGl, RenderPipelineSeverity.Warning, "s52.geometry_fallback", "fallback", objectClass = "LIGHTS"),
                RenderPipelineDiagnostic(RenderPipelineStage.Geometry, RenderPipelineSeverity.Error, "s57.missing_vector", "missing", objectClass = "DEPCNT")
            )
        )

        assertEquals(0, report.infoCount)
        assertEquals(2, report.warningCount)
        assertEquals(1, report.errorCount)
        assertEquals(1, report.countByStage()[RenderPipelineStage.Adapter])
        assertEquals(1, report.countByCode()["s52.geometry_fallback"])
        assertEquals(2, report.countByObjectClass()["LIGHTS"])
        assertTrue("warnings=2" in report.toPlainText())
        assertTrue("\"s52.geometry_fallback\":1" in report.toJson())
    }

    @Test
    fun renderedFrameSummaryCombinesFrameAndS52Diagnostics() {
        val adapterDiagnostic = RenderPipelineDiagnostic(RenderPipelineStage.Adapter, RenderPipelineSeverity.Warning, "adapter.warning", "adapter")
        val webglDiagnostic = RenderPipelineDiagnostic(RenderPipelineStage.S52WebGl, RenderPipelineSeverity.Warning, "webgl.warning", "webgl")
        val summary = RenderedFrameSummary(
            widthPx = 800,
            heightPx = 600,
            message = "rendered",
            s52 = S52RenderSummary(diagnostics = listOf(webglDiagnostic), diagnosticCount = 1),
            pipelineDiagnostics = listOf(adapterDiagnostic)
        )

        val report = summary.pipelineDiagnosticReport()

        assertEquals(2, report.diagnostics.size)
        assertEquals(1, report.countByStage()[RenderPipelineStage.Adapter])
        assertEquals(1, report.countByStage()[RenderPipelineStage.S52WebGl])
    }


    @Test
    fun snapshotDiagnosticExportIncludesArtifactS52AndPipelineSections() {
        val diagnostic = RenderPipelineDiagnostic(RenderPipelineStage.S52WebGl, RenderPipelineSeverity.Warning, "s52.geometry_fallback", "fallback")
        val export = RenderSnapshotDiagnosticExport(
            cellId = "US5TEST",
            paletteName = "daybright",
            scaleDenominator = 12_000.0,
            widthPx = 640,
            heightPx = 480,
            renderMessage = "rendered",
            artifact = RenderedArtifactReport(
                widthPx = 640,
                heightPx = 480,
                featureCount = 3,
                visibleFeatureCount = 2,
                onscreenFeatureCount = 2,
                offscreenFeatureCount = 1,
                clippedFeatureCount = 0,
                pointFeatureCount = 1,
                lineFeatureCount = 1,
                polygonFeatureCount = 1,
                emptyGeometryCount = 0,
                centerCrosshairHitCount = 1,
                depthMeshVertexCount = 0,
                depthMeshTriangleCount = 0,
                fallbackPlaceholderCount = 0
            ),
            s52 = S52RenderSummary(commandCount = 4, drawCallCount = 3, diagnostics = listOf(diagnostic), diagnosticCount = 1),
            pipeline = RenderPipelineDiagnosticReport(listOf(diagnostic)),
            importSummary = "import ok"
        )

        val json = export.toJson()

        assertTrue("\"cellId\":\"US5TEST\"" in json)
        assertTrue("\"artifact\":{\"widthPx\":640" in json)
        assertTrue("\"s52\":{\"profile\":\"none\"" in json)
        assertTrue("\"pipeline\":{\"total\":1" in json)
        assertTrue("\"importSummary\":\"import ok\"" in json)
    }

}
