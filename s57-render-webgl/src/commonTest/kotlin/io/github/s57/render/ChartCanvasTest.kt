package io.github.s57.render

import io.github.s57.core.GeoBounds
import io.github.s57.core.GeoPoint
import io.github.s57.core.S57CellSummary
import io.github.s57.core.S57Dataset
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChartCanvasTest {
    @Test
    fun canvasCommandsRenderAndExposeQueryableStatus() {
        val engine = S57WebGlEngine()
        engine.importDataset(testDataset())
        var drawCount = 0
        val canvas = S57ChartCanvas(
            engine = engine,
            frameRenderer = ChartCanvasFrameRenderer { frame ->
                drawCount++
                frame.summary().copy(message = "drawn ${frame.request.cellId}")
            },
            initialSize = ScreenSize(800, 600)
        )
        val events = mutableListOf<ChartCanvasEvent>()
        canvas.addListener { events += it }

        canvas.dispatch(ChartCanvasCommand.ShowCharts(listOf("US5TEST")))
        canvas.dispatch(ChartCanvasCommand.MoveCursor(ScreenPoint(400.0, 300.0)))
        canvas.dispatch(ChartCanvasCommand.Zoom(2.0, ScreenPoint(400.0, 300.0)))

        val status = canvas.status()
        assertEquals(listOf("US5TEST"), status.displayedChartIds)
        assertEquals("US5TEST", status.activeChartId)
        assertNotNull(status.scaleDenominator)
        assertNotNull(status.cursorGeoPoint)
        assertTrue(drawCount >= 2)
        assertTrue(events.any { it is ChartCanvasEvent.ChartRendered })
        assertTrue(events.any { it is ChartCanvasEvent.CursorMoved })
    }

    @Test
    fun pressPublishesSelectionEventsFromLastFrame() {
        val engine = S57WebGlEngine()
        engine.importDataset(testDataset())
        val canvas = S57ChartCanvas(
            engine = engine,
            frameRenderer = ChartCanvasFrameRenderer { it.summary() },
            initialSize = ScreenSize(800, 600)
        )
        val events = mutableListOf<ChartCanvasEvent>()
        canvas.addListener { events += it }

        canvas.dispatch(ChartCanvasCommand.ShowCharts(listOf("US5TEST")))
        canvas.dispatch(ChartCanvasCommand.Press(ScreenPoint(400.0, 300.0), radiusPx = 80.0))

        val pressed = events.filterIsInstance<ChartCanvasEvent.Pressed>().last()
        assertTrue(pressed.hits.isNotEmpty())
        assertEquals("BOYLAT", pressed.hits.first().objectClass)
        assertTrue(events.any { it is ChartCanvasEvent.ObjectSelected })
        assertEquals("BOYLAT", canvas.status().lastSelectedObject?.objectClass)
    }

    private fun testDataset(): S57Dataset = S57Dataset(
        summary = S57CellSummary(
            cellId = "US5TEST",
            name = "Test",
            bounds = GeoBounds(-75.0, 39.0, -73.0, 41.0),
            featureCount = 1
        ),
        features = listOf(
            S57Feature(
                id = 7L,
                objectClass = "BOYLAT",
                geometry = S57Geometry.Point(GeoPoint(-74.0, 40.0))
            )
        )
    )
}
