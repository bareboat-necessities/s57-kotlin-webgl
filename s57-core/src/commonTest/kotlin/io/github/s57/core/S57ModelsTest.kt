package io.github.s57.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class S57ModelsTest {
    @Test
    fun computesLineBounds() {
        val geometry = S57Geometry.LineString(listOf(GeoPoint(-74.1, 40.0), GeoPoint(-73.9, 40.2)))
        val bounds = geometry.boundsOrNull()
        assertEquals(-74.1, bounds?.minLon)
        assertTrue(bounds!!.intersects(GeoBounds(-74.0, 39.9, -73.8, 40.3)))
    }
}
