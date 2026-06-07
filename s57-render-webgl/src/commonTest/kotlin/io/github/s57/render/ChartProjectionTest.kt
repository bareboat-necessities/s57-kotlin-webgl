package io.github.s57.render

import io.github.s57.core.GeoBounds
import io.github.s57.core.GeoPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartProjectionTest {
    @Test
    fun projectsCenterToViewportCenter() {
        val projection = ChartProjection(
            bounds = GeoBounds(-75.0, 39.0, -73.0, 41.0),
            viewport = ScreenSize(1000, 500)
        )
        val screen = projection.project(GeoPoint(-74.0, 40.0))
        assertEquals(500.0, screen.x, 1.0e-6)
        assertEquals(250.0, screen.y, 1.0e-6)
    }

    @Test
    fun unprojectsScreenCenterToGeoCenterWithTiltAndRotation() {
        val projection = ChartProjection(
            bounds = GeoBounds(-75.0, 39.0, -73.0, 41.0),
            viewport = ScreenSize(1000, 500),
            rotationDegrees = 25.0,
            tiltDegrees = 20.0
        )
        val geo = projection.unproject(ScreenPoint(500.0, 250.0))
        assertTrue(abs(geo.lon + 74.0) < 1.0e-6)
        assertTrue(abs(geo.lat - 40.0) < 1.0e-6)
    }
}
