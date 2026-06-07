package io.github.s57.render

import io.github.s57.core.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InteractionModelsTest {
    @Test
    fun clickEmitsObjectQueryEvent() {
        val events = mutableListOf<ChartUserEvent>()
        val hitTester = object : ChartHitTester {
            override fun hitTest(point: ScreenPoint, radiusPx: Double): List<ChartHitResult> =
                listOf(ChartHitResult(7L, "BOYLAT", distancePx = 1.5))
        }
        val controller = ChartInteractionController(hitTester, object : ChartInteractionListener {
            override fun onUserEvent(event: ChartUserEvent) { events += event }
        })

        controller.handlePointer(ChartPointerEvent(1, PointerPhase.Down, ScreenPoint(10.0, 20.0), PointerKind.Mouse))
        controller.handlePointer(ChartPointerEvent(1, PointerPhase.Up, ScreenPoint(12.0, 21.0), PointerKind.Mouse))

        val click = events.single() as ChartUserEvent.ClickObject
        assertEquals("BOYLAT", click.hits.single().objectClass)
    }

    @Test
    fun wheelEmitsScrollAndZoom() {
        val events = mutableListOf<ChartUserEvent>()
        val controller = ChartInteractionController(listener = object : ChartInteractionListener {
            override fun onUserEvent(event: ChartUserEvent) { events += event }
        })
        controller.handleWheel(ChartWheelEvent(ScreenPoint(100.0, 200.0), deltaX = 0.0, deltaY = -120.0))
        assertTrue(events.any { it is ChartUserEvent.Scroll && it.direction == ScrollDirection.Up })
        assertTrue(events.any { it is ChartUserEvent.Zoom && it.factor > 1.0 })
    }

    @Test
    fun cameraSupportsRotationTiltAndZoom() {
        val camera = ChartCameraState(GeoPoint(-74.0, 40.0), zoom = 1000.0)
            .zoomedBy(2.0)
            .rotatedBy(-15.0)
            .tiltedBy(12.0)
        assertEquals(2000.0, camera.zoom)
        assertEquals(345.0, camera.rotationDegrees)
        assertEquals(12.0, camera.tiltDegrees)
    }
}
