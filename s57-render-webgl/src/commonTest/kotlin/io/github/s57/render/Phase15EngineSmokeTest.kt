package io.github.s57.render

import io.github.s57.core.GeoBounds
import io.github.s57.core.GeoPoint
import io.github.s57.core.S57CellSummary
import io.github.s57.core.S57Dataset
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import io.github.s57.core.S57Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Phase15EngineSmokeTest {
    @Test
    fun builtInSampleDatasetProducesVisibleStaticFrame() {
        val dataset = phase15SampleDataset()
        val engine = S57WebGlEngine()
        val imported = engine.importDataset(dataset)

        assertEquals("PHASE15-SMOKE", imported.cell.cellId)
        assertEquals(4, engine.stats().featureCount)
        assertEquals(listOf("PHASE15-SMOKE"), engine.listCells().map { it.cellId })

        val request = chartRenderRequestForCell(
            cell = dataset.summary,
            widthPx = 1280,
            heightPx = 720,
            scaleDenominator = 40_000.0
        ).copy(centerCrosshair = CenterCrosshairConfig(enabled = true, queryOnRender = true, hitRadiusPx = 48.0))

        val rendered = engine.render(request)
        rendered.validateMinimum(minVisibleFeatures = 1)
        assertEquals(4, rendered.frame.queriedFeatureCount)
        assertEquals(4, rendered.frame.projectedFeatures.size)
        assertTrue(rendered.diagnostics.visibleFeatureCount >= 1)
        assertEquals(0, rendered.diagnostics.fallbackPlaceholderCount)
        assertTrue(rendered.toSvgSnapshot(includeLabels = true).contains("PHASE15"))
    }

    @Test
    fun clearRemovesPreviousImportedCellBeforeNewBrowserImport() {
        val engine = S57WebGlEngine()
        engine.importDataset(phase15SampleDataset())
        assertEquals(1, engine.listCells().size)

        engine.clear()

        assertEquals(0, engine.listCells().size)
        assertEquals(0, engine.stats().featureCount)
    }
}

private fun phase15SampleDataset(): S57Dataset = S57Dataset(
    summary = S57CellSummary(
        cellId = "PHASE15-SMOKE",
        name = "PHASE15-SMOKE",
        bounds = GeoBounds(-75.0, 39.0, -73.0, 41.0),
        featureCount = 4
    ),
    features = listOf(
        S57Feature(
            id = 1,
            objectClass = "DEPARE",
            attributes = mapOf("DRVAL1" to S57Value.Decimal(0.0), "DRVAL2" to S57Value.Decimal(10.0)),
            geometry = S57Geometry.Polygon(listOf(listOf(
                GeoPoint(-74.8, 39.2),
                GeoPoint(-73.2, 39.2),
                GeoPoint(-73.2, 40.8),
                GeoPoint(-74.8, 40.8),
                GeoPoint(-74.8, 39.2)
            )))
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
            geometry = S57Geometry.MultiPoint(listOf(GeoPoint(-74.1, 40.18), GeoPoint(-73.85, 40.28), GeoPoint(-74.0, 40.0)))
        ),
        S57Feature(
            id = 4,
            objectClass = "BOYLAT",
            geometry = S57Geometry.Point(GeoPoint(-73.65, 40.52))
        )
    )
)
