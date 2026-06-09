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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Phase21VisualSmokeTest {
    @Test
    fun validatesRecognizableOnscreenChartContent() {
        val engine = S57WebGlEngine()
        val imported = engine.importDataset(visualDataset())
        val rendered = engine.render(chartRenderRequestForCell(imported.cell, 1000, 700))
        val report = noaaEncVisualSmokeReport(imported, rendered)

        report.validate(
            minDecodedFeatures = 4,
            minIndexedFeatures = 4,
            minQueriedFeatures = 4,
            minAdaptedFeatures = 4,
            minProjectedFeatures = 4,
            minOnscreenFeatures = 4,
            minObjectClasses = 4
        )
        assertEquals(4, report.objectClassDiversity)
        assertTrue(report.objectClassCounts.keys.containsAll(listOf("DEPARE", "DEPCNT", "SOUNDG", "BOYLAT")))
        assertTrue("onscreenFeatures=" in report.toPlainText())
    }

    @Test
    fun failsWhenAllFeaturesAreOffscreen() {
        val artifact = RenderedArtifactReport(
            widthPx = 800,
            heightPx = 600,
            featureCount = 1,
            visibleFeatureCount = 0,
            onscreenFeatureCount = 0,
            offscreenFeatureCount = 1,
            clippedFeatureCount = 0,
            pointFeatureCount = 1,
            lineFeatureCount = 0,
            polygonFeatureCount = 0,
            emptyGeometryCount = 0,
            centerCrosshairHitCount = 0,
            depthMeshVertexCount = 0,
            depthMeshTriangleCount = 0
        )
        val report = NoaaEncVisualSmokeReport(
            cellId = "OFFSCREEN",
            hasBounds = true,
            rawFeatureCount = 1,
            rawVectorCount = 1,
            decodedFeatureCount = 1,
            geometryDiagnosticCount = 0,
            indexedFeatureCount = 1,
            queriedFeatureCount = 1,
            adaptedFeatureCount = 1,
            projectedFeatureCount = 1,
            onscreenFeatureCount = 0,
            offscreenFeatureCount = 1,
            clippedFeatureCount = 0,
            emptyGeometryCount = 0,
            objectClassCounts = mapOf("SOUNDG" to 1),
            artifact = artifact
        )

        assertFailsWith<IllegalArgumentException> { report.validate(minOnscreenFeatures = 1) }
    }

    private fun visualDataset(): S57Dataset = S57Dataset(
        summary = S57CellSummary(
            cellId = "PHASE21",
            name = "PHASE21",
            bounds = GeoBounds(-74.5, 40.0, -73.5, 41.0),
            featureCount = 4
        ),
        features = listOf(
            S57Feature(
                id = 1,
                objectClass = "DEPARE",
                attributes = mapOf("DRVAL1" to S57Value.Decimal(0.0), "DRVAL2" to S57Value.Decimal(10.0)),
                geometry = S57Geometry.Polygon(
                    listOf(
                        listOf(
                            GeoPoint(-74.4, 40.1),
                            GeoPoint(-73.6, 40.1),
                            GeoPoint(-73.6, 40.9),
                            GeoPoint(-74.4, 40.9),
                            GeoPoint(-74.4, 40.1)
                        )
                    )
                )
            ),
            S57Feature(
                id = 2,
                objectClass = "DEPCNT",
                geometry = S57Geometry.LineString(listOf(GeoPoint(-74.4, 40.5), GeoPoint(-73.6, 40.5)))
            ),
            S57Feature(
                id = 3,
                objectClass = "SOUNDG",
                attributes = mapOf("VALSOU" to S57Value.Decimal(5.0)),
                geometry = S57Geometry.Point(GeoPoint(-74.0, 40.55))
            ),
            S57Feature(
                id = 4,
                objectClass = "BOYLAT",
                geometry = S57Geometry.Point(GeoPoint(-73.85, 40.35))
            )
        )
    )
}
