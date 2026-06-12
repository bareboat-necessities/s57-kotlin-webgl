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
    fun showChartsRendersMultipleCellsOnOneFrame() {
        val engine = S57WebGlEngine()
        engine.importDataset(testDataset())
        engine.importDataset(testDataset("US5TWO", GeoPoint(-73.8, 40.1), "BOYCAR"))
        var lastFrame: StaticChartFrame? = null
        val canvas = S57ChartCanvas(
            engine = engine,
            frameRenderer = ChartCanvasFrameRenderer { frame ->
                lastFrame = frame
                frame.summary()
            },
            initialSize = ScreenSize(800, 600)
        )

        canvas.dispatch(ChartCanvasCommand.ShowCharts(listOf("US5TEST", "US5TWO")))

        assertEquals(listOf("US5TEST", "US5TWO"), canvas.status().displayedChartIds)
        assertEquals(listOf("US5TEST", "US5TWO"), lastFrame?.request?.renderCellIds)
        assertTrue(lastFrame?.projectedFeatures?.any { it.objectClass == "BOYCAR" } == true)
    }


    @Test
    fun interactiveCommandsCanBeCoalescedWithoutImmediateRedraw() {
        val engine = S57WebGlEngine()
        engine.importDataset(testDataset())
        var drawCount = 0
        val canvas = S57ChartCanvas(
            engine = engine,
            frameRenderer = ChartCanvasFrameRenderer { frame ->
                drawCount++
                frame.summary()
            },
            initialSize = ScreenSize(800, 600)
        )

        canvas.dispatch(ChartCanvasCommand.ShowCharts(listOf("US5TEST")))
        val afterInitialDraw = drawCount
        canvas.dispatch(ChartCanvasCommand.Zoom(1.2, ScreenPoint(400.0, 300.0), redraw = false))
        canvas.dispatch(ChartCanvasCommand.Scroll(ScreenDelta(25.0, 0.0), redraw = false))

        assertEquals(afterInitialDraw, drawCount)
        canvas.dispatch(ChartCanvasCommand.Render("coalesced interaction"))
        assertEquals(afterInitialDraw + 1, drawCount)
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

    private fun testDataset(
        cellId: String = "US5TEST",
        point: GeoPoint = GeoPoint(-74.0, 40.0),
        objectClass: String = "BOYLAT"
    ): S57Dataset = S57Dataset(
        summary = S57CellSummary(
            cellId = cellId,
            name = "Test",
            bounds = GeoBounds(-75.0, 39.0, -73.0, 41.0),
            featureCount = 1
        ),
        features = listOf(
            S57Feature(
                id = 7L,
                objectClass = objectClass,
                geometry = S57Geometry.Point(point)
            )
        )
    )
}
