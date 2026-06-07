package io.github.s57.render

import io.github.s57.core.GeoBounds
import io.github.s57.core.GeoPoint
import io.github.s57.core.S57CellSummary
import io.github.s57.core.S57Dataset
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import io.github.s57.core.S57Value
import io.github.s57.index.InMemoryS57IndexStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class S57StaticChartRendererTest {
    @Test
    fun preparesStaticFrameFromIndexedFeatures() {
        val store = InMemoryS57IndexStore()
        store.importDataset(sampleDataset())
        val request = request()
        val frame = S57StaticChartRenderer(store).prepareFrame(request)
        assertEquals(3, frame.queriedFeatureCount)
        assertEquals(3, frame.projectedFeatures.size)
        assertTrue(frame.adaptedFeatureCount >= 2)
        assertTrue(frame.projectedFeatures.any { it.objectClass == "DEPARE" })
        assertTrue(frame.projectedFeatures.any { it.objectClass == "DEPCNT" })
    }

    @Test
    fun centerCrosshairHitTestFindsAreaAtCenter() {
        val store = InMemoryS57IndexStore()
        store.importDataset(sampleDataset())
        val frame = S57StaticChartRenderer(store).prepareFrame(
            request().copy(centerCrosshair = CenterCrosshairConfig(enabled = true, queryOnRender = true))
        )
        assertNotNull(frame.centerCrosshairHits.firstOrNull { it.objectClass == "DEPARE" })
    }

    private fun request(): ChartRenderRequest = ChartRenderRequest(
        cellId = "TESTCELL",
        bounds = GeoBounds(-75.0, 39.0, -73.0, 41.0),
        widthPx = 1000,
        heightPx = 500,
        scaleDenominator = 40000.0,
        camera = ChartCameraState(
            center = GeoPoint(-74.0, 40.0),
            zoom = 40000.0,
            rotationDegrees = 0.0,
            tiltDegrees = 10.0,
            viewport = ScreenSize(1000, 500)
        ),
        renderMode = ChartRenderMode.Tilted2D
    )

    private fun sampleDataset(): S57Dataset = S57Dataset(
        summary = S57CellSummary(
            cellId = "TESTCELL",
            name = "TESTCELL",
            bounds = GeoBounds(-75.0, 39.0, -73.0, 41.0),
            featureCount = 3
        ),
        features = listOf(
            S57Feature(
                id = 1,
                objectClass = "DEPARE",
                attributes = mapOf("DRVAL1" to S57Value.Decimal(0.0), "DRVAL2" to S57Value.Decimal(10.0)),
                geometry = S57Geometry.Polygon(
                    listOf(
                        listOf(
                            GeoPoint(-74.8, 39.2),
                            GeoPoint(-73.2, 39.2),
                            GeoPoint(-73.2, 40.8),
                            GeoPoint(-74.8, 40.8),
                            GeoPoint(-74.8, 39.2)
                        )
                    )
                )
            ),
            S57Feature(
                id = 2,
                objectClass = "DEPCNT",
                geometry = S57Geometry.LineString(listOf(GeoPoint(-74.8, 40.0), GeoPoint(-73.2, 40.0)))
            ),
            S57Feature(
                id = 3,
                objectClass = "SOUNDG",
                attributes = mapOf("VALSOU" to S57Value.Decimal(4.2)),
                geometry = S57Geometry.Point(GeoPoint(-74.0, 40.0))
            )
        )
    )
}
