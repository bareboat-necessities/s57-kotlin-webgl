package io.github.s57.render

import io.github.s57.core.GeoBounds
import io.github.s57.core.GeoPoint
import io.github.s57.core.S57CellSummary
import io.github.s57.core.S57Dataset
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class S57WebGlEngineTest {
    @Test
    fun importsListsAndRendersStaticCell() {
        val bounds = GeoBounds(-74.0, 40.0, -73.9, 40.1)
        val dataset = S57Dataset(
            summary = S57CellSummary("US5TEST", "Test cell", bounds = bounds, featureCount = 2),
            features = listOf(
                S57Feature(1, "DEPARE", geometry = S57Geometry.Polygon(listOf(listOf(
                    GeoPoint(-74.0, 40.0), GeoPoint(-73.9, 40.0), GeoPoint(-73.9, 40.1), GeoPoint(-74.0, 40.0)
                ))),
                S57Feature(2, "BOYLAT", geometry = S57Geometry.Point(GeoPoint(-73.95, 40.05)))
            )
        )
        val engine = S57WebGlEngine()
        val imported = engine.importDataset(dataset)
        assertEquals("US5TEST", imported.cell.cellId)
        assertEquals(2, engine.stats().featureCount)
        assertEquals(listOf("US5TEST"), engine.listCells().map { it.cellId })

        val request = chartRenderRequestForCell(dataset.summary, widthPx = 800, heightPx = 600)
        val rendered = engine.render(request)
        rendered.validateMinimum(minVisibleFeatures = 2)
        assertTrue(rendered.toSvgSnapshot().contains("<svg"))
    }

    @Test
    fun centerCrosshairReturnsPointedFeature() {
        val bounds = GeoBounds(-74.0, 40.0, -73.9, 40.1)
        val dataset = S57Dataset(
            summary = S57CellSummary("US5CENTER", "Center cell", bounds = bounds, featureCount = 1),
            features = listOf(S57Feature(10, "SOUNDG", geometry = S57Geometry.Point(GeoPoint(-73.95, 40.05))))
        )
        val engine = S57WebGlEngine()
        engine.importDataset(dataset)
        val request = chartRenderRequestForCell(dataset.summary, widthPx = 800, heightPx = 600)
            .copy(centerCrosshair = CenterCrosshairConfig(enabled = true, queryOnRender = true, hitRadiusPx = 24.0))
        val hits = engine.centerCrosshairHits(request)
        assertTrue(hits.any { it.featureId == 10L })
    }
}
