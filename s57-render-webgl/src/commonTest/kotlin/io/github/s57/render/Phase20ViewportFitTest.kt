package io.github.s57.render

import io.github.s57.core.GeoBounds
import io.github.s57.core.GeoPoint
import io.github.s57.core.S57CellSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Phase20ViewportFitTest {
    @Test
    fun expandsTallCellBoundsToViewportAspectWithPadding() {
        val source = GeoBounds(-74.0, 40.0, -73.9, 41.0)
        val viewport = ScreenSize(1000, 500)
        val fitted = fitChartBoundsToViewport(source, viewport, paddingFraction = 0.10)

        assertTrue(fitted.minLon < source.minLon)
        assertTrue(fitted.maxLon > source.maxLon)
        assertTrue(fitted.minLat <= source.minLat)
        assertTrue(fitted.maxLat >= source.maxLat)
        assertEquals(source.center().lon, fitted.center().lon, 1.0e-9)
        assertEquals(source.center().lat, fitted.center().lat, 1.0e-9)

        val aspect = (fitted.maxLon - fitted.minLon) / (fitted.maxLat - fitted.minLat)
        assertEquals(2.0, aspect, 1.0e-9)
    }

    @Test
    fun createsAutoFitRenderRequestForCell() {
        val cell = S57CellSummary(
            cellId = "US5P20",
            name = "US5P20",
            bounds = GeoBounds(-75.0, 39.0, -73.0, 40.0),
            featureCount = 1
        )

        val request = chartRenderRequestForCell(cell, widthPx = 1200, heightPx = 600)

        assertEquals("US5P20", request.cellId)
        assertEquals(ScreenSize(1200, 600), request.camera.viewport)
        assertEquals(request.scaleDenominator, request.camera.zoom)
        assertTrue(request.scaleDenominator > 500.0)
        assertTrue(request.bounds.minLon <= -75.0)
        assertTrue(request.bounds.maxLon >= -73.0)
        assertTrue(request.bounds.minLat < 39.0 || request.bounds.maxLat > 40.0)
        assertEquals(request.bounds.center(), request.camera.center)
    }

    @Test
    fun reportsOffscreenAndClippedFeatureCounts() {
        val request = ChartRenderRequest(
            cellId = "US5P20",
            bounds = GeoBounds(-75.0, 39.0, -73.0, 41.0),
            widthPx = 100,
            heightPx = 100,
            scaleDenominator = 40000.0,
            camera = ChartCameraState(GeoPoint(-74.0, 40.0), 40000.0, viewport = ScreenSize(100, 100))
        )
        val frame = StaticChartFrame(
            request = request,
            queriedFeatureCount = 3,
            adaptedFeatureCount = 3,
            projectedFeatures = listOf(
                projected(1, ScreenBounds(10.0, 10.0, 20.0, 20.0)),
                projected(2, ScreenBounds(-10.0, 10.0, 10.0, 20.0)),
                projected(3, ScreenBounds(200.0, 200.0, 210.0, 210.0))
            )
        )
        val report = analyzeRenderedArtifact(frame)

        assertEquals(2, frame.onscreenFeatureCount)
        assertEquals(1, frame.offscreenFeatureCount)
        assertEquals(1, frame.clippedFeatureCount)
        assertEquals(2, report.onscreenFeatureCount)
        assertEquals(1, report.offscreenFeatureCount)
        assertEquals(1, report.clippedFeatureCount)
        assertTrue("offscreen=1" in report.toPlainText())
    }

    private fun projected(id: Long, bounds: ScreenBounds): ProjectedFeature = ProjectedFeature(
        featureId = id,
        objectClass = "TEST",
        geometry = ProjectedGeometry.Point(ScreenPoint(bounds.minX, bounds.minY)),
        geoBounds = null,
        screenBounds = bounds
    )
}
