package io.github.s57.index

import io.github.s57.core.GeoBounds
import io.github.s57.core.GeoPoint
import io.github.s57.core.S57CellSummary
import io.github.s57.core.S57Dataset
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class S57IndexStoreTest {
    @Test
    fun inMemoryStoreQueriesByBounds() {
        val store = InMemoryS57IndexStore()
        store.putCell(S57CellSummary(cellId = "US5TEST", name = "Test cell"))
        store.putFeatures(
            "US5TEST",
            listOf(S57Feature(id = 1, objectClass = "BOYLAT", geometry = S57Geometry.Point(GeoPoint(-74.0, 40.0))))
        )
        assertEquals(1, store.queryFeatures("US5TEST", GeoBounds(-74.1, 39.9, -73.9, 40.1)).size)
        assertEquals(0, store.queryFeatures("US5TEST", GeoBounds(-75.0, 39.9, -74.9, 40.1)).size)
    }

    @Test
    fun importDatasetBuildsSpatialBinsAndStats() {
        val dataset = S57Dataset(
            summary = S57CellSummary(cellId = "US5TEST", name = "Test cell", bounds = GeoBounds(-74.2, 39.8, -73.8, 40.2), featureCount = 3),
            features = listOf(
                S57Feature(
                    id = 1,
                    objectClass = "DEPARE",
                    geometry = S57Geometry.Polygon(
                        listOf(
                            listOf(
                                GeoPoint(-74.1, 39.9),
                                GeoPoint(-73.9, 39.9),
                                GeoPoint(-73.9, 40.1),
                                GeoPoint(-74.1, 39.9)
                            )
                        )
                    )
                ),
                S57Feature(id = 2, objectClass = "BOYLAT", geometry = S57Geometry.Point(GeoPoint(-74.0, 40.0))),
                S57Feature(id = 3, objectClass = "EMPTY", geometry = S57Geometry.Empty)
            )
        )
        val store = InMemoryS57IndexStore(S57SpatialBinIndex(SpatialBinConfig(0.05, 0.05)))
        val report = store.importDataset(dataset)
        assertEquals("US5TEST", report.cellId)
        assertEquals(3, report.featureCount)
        assertEquals(2, report.indexedFeatureCount)
        assertEquals(1, report.emptyGeometryCount)
        assertTrue(report.binCount > 0)
        assertEquals(1, store.stats().cellCount)
        assertEquals(3, store.stats().featureCount)
    }

    @Test
    fun queryCanFilterObjectClassesAndApplyLimit() {
        val store = InMemoryS57IndexStore()
        store.putCell(S57CellSummary(cellId = "US5TEST", name = "Test cell"))
        store.putFeatures(
            "US5TEST",
            listOf(
                S57Feature(id = 1, objectClass = "BOYLAT", geometry = S57Geometry.Point(GeoPoint(-74.00, 40.00))),
                S57Feature(id = 2, objectClass = "BCNLAT", geometry = S57Geometry.Point(GeoPoint(-74.01, 40.00))),
                S57Feature(id = 3, objectClass = "DEPARE", geometry = S57Geometry.Point(GeoPoint(-74.02, 40.00)))
            )
        )
        val hits = store.queryFeatures(
            S57FeatureQuery(
                cellId = "US5TEST",
                bounds = GeoBounds(-74.1, 39.9, -73.9, 40.1),
                objectClasses = setOf("BOYLAT", "BCNLAT"),
                limit = 1
            )
        )
        assertEquals(1, hits.size)
        assertTrue(hits.single().objectClass in setOf("BOYLAT", "BCNLAT"))
    }

    @Test
    fun spatialBinIndexMapsBoundsAcrossMultipleBins() {
        val index = S57SpatialBinIndex(SpatialBinConfig(lonStepDegrees = 0.1, latStepDegrees = 0.1))
        val bins = index.binsForBounds("CELL", GeoBounds(-74.15, 39.95, -73.95, 40.15))
        assertTrue(bins.size >= 6)
        assertTrue(SpatialBinId("CELL", -742, 399) in bins)
    }
}
