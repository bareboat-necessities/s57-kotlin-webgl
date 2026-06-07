package io.github.s57.render

import io.github.s57.core.GeoBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RenderedArtifactDiagnosticsTest {
    @Test
    fun analyzesVisibleFrameAndExportsSvgSnapshot() {
        val frame = sampleFrame()
        val report = analyzeRenderedArtifact(frame)
        assertEquals(3, report.featureCount)
        assertEquals(3, report.visibleFeatureCount)
        assertEquals(1, report.pointFeatureCount)
        assertEquals(1, report.lineFeatureCount)
        assertEquals(1, report.polygonFeatureCount)
        assertEquals(1, report.centerCrosshairHitCount)
        assertEquals(3, report.depthMeshVertexCount)
        assertEquals(1, report.depthMeshTriangleCount)
        report.validateMinimum(minVisibleFeatures = 3)

        val svg = renderedArtifactSvgSnapshot(frame, includeLabels = true)
        assertTrue("<polygon" in svg)
        assertTrue("<polyline" in svg)
        assertTrue("<circle" in svg)
        assertTrue("DEPARE" in svg)
    }

    @Test
    fun validationFailsForEmptyRenderedArtifact() {
        val empty = StaticChartFrame(
            request = sampleRequest(),
            queriedFeatureCount = 0,
            adaptedFeatureCount = 0,
            projectedFeatures = emptyList()
        )
        val report = analyzeRenderedArtifact(empty)
        assertFailsWith<IllegalArgumentException> { report.validateMinimum() }
    }

    private fun sampleFrame(): StaticChartFrame {
        val request = sampleRequest()
        val polygon = ProjectedFeature(
            featureId = 1,
            objectClass = "DEPARE",
            geometry = ProjectedGeometry.Polygon(listOf(listOf(ScreenPoint(40.0, 40.0), ScreenPoint(180.0, 40.0), ScreenPoint(180.0, 140.0), ScreenPoint(40.0, 140.0)))),
            geoBounds = GeoBounds(-75.0, 39.0, -74.0, 40.0),
            screenBounds = ScreenBounds(40.0, 40.0, 180.0, 140.0)
        )
        val line = ProjectedFeature(
            featureId = 2,
            objectClass = "DEPCNT",
            geometry = ProjectedGeometry.LineString(listOf(ScreenPoint(20.0, 200.0), ScreenPoint(240.0, 210.0))),
            geoBounds = GeoBounds(-75.0, 39.0, -74.0, 39.5),
            screenBounds = ScreenBounds(20.0, 200.0, 240.0, 210.0)
        )
        val point = ProjectedFeature(
            featureId = 3,
            objectClass = "SOUNDG",
            geometry = ProjectedGeometry.Point(ScreenPoint(320.0, 220.0)),
            geoBounds = GeoBounds(-74.5, 39.5, -74.5, 39.5),
            screenBounds = ScreenBounds(320.0, 220.0, 320.0, 220.0)
        )
        return StaticChartFrame(
            request = request,
            queriedFeatureCount = 3,
            adaptedFeatureCount = 3,
            projectedFeatures = listOf(polygon, line, point),
            centerCrosshairHits = listOf(ChartHitResult(1, "DEPARE")),
            depthMesh = DepthMeshTile(
                bounds = request.bounds,
                vertices = listOf(
                    DepthMeshVertex(0f, 0f, -1f, 1f),
                    DepthMeshVertex(1f, 0f, -2f, 2f),
                    DepthMeshVertex(0f, 1f, -3f, 3f)
                ),
                triangleIndices = listOf(0, 1, 2)
            )
        )
    }

    private fun sampleRequest(): ChartRenderRequest = ChartRenderRequest(
        cellId = "TESTCELL",
        bounds = GeoBounds(-75.0, 39.0, -73.0, 41.0),
        widthPx = 800,
        heightPx = 600,
        scaleDenominator = 20_000.0,
        centerCrosshair = CenterCrosshairConfig(enabled = true, queryOnRender = true)
    )
}
