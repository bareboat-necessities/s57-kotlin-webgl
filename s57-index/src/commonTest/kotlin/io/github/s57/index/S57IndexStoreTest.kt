package io.github.s57.index

import io.github.s57.core.GeoBounds
import io.github.s57.core.GeoPoint
import io.github.s57.core.S57CellSummary
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import kotlin.test.Test
import kotlin.test.assertEquals

class S57IndexStoreTest {
    @Test
    fun inMemoryStoreQueriesByBounds() {
        val store = InMemoryS57IndexStore()
        store.putCell(S57CellSummary(cellId = "US5TEST", name = "Test cell"))
        store.putFeatures("US5TEST", listOf(S57Feature(1, "BOYLAT", geometry = S57Geometry.Point(GeoPoint(-74.0, 40.0)))))
        assertEquals(1, store.queryFeatures("US5TEST", GeoBounds(-74.1, 39.9, -73.9, 40.1)).size)
    }
}
