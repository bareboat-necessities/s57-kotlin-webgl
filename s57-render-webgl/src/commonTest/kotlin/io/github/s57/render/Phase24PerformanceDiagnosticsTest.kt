package io.github.s57.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Phase24PerformanceDiagnosticsTest {
    @Test
    fun formatsTimingReportWithTotal() {
        val timing = EngineTimingReport(
            decodeMs = 1.11149,
            indexMs = 2.22251,
            framePrepareMs = 3.0,
            artifactAnalyzeMs = 4.0
        )

        assertEquals(10.334, timing.rounded().totalMs)
        assertTrue("decodeMs=1.111" in timing.toPlainText())
        assertTrue("totalMs=10.334" in timing.toPlainText())
    }

    @Test
    fun summarizesMetricSamplesByName() {
        val collector = PerformanceMetricCollector()
        collector.add("render.total", 10.0)
        collector.add("render.total", 20.0)
        collector.add("import.total", 5.0)

        val summaries = collector.summaries()
        val render = summaries.first { it.name == "render.total" }
        assertEquals(2, render.count)
        assertEquals(10.0, render.minMs)
        assertEquals(20.0, render.maxMs)
        assertEquals(15.0, render.avgMs)
        assertTrue("performanceMetrics samples=3" in collector.toPlainText())
    }

    @Test
    fun buildsPerformanceFrameReportFromRenderResult() {
        val timing = EngineTimingReport(framePrepareMs = 2.0, artifactAnalyzeMs = 1.0)
        val report = PerformanceFrameReport(
            cellId = "CELL",
            featureCount = 4,
            onscreenFeatureCount = 3,
            offscreenFeatureCount = 1,
            scaleDenominator = 12000.0,
            timing = timing
        )

        assertTrue("performance cell=CELL" in report.toPlainText())
        assertTrue("framePrepareMs=2" in report.toPlainText())
    }
}
