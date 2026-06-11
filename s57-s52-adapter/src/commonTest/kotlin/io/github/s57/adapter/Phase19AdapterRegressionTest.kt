package io.github.s57.adapter

import io.github.s57.core.GeoPoint
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import io.github.s57.core.S57Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Phase19AdapterRegressionTest {
    @Test
    fun splitsMultiPolygonInsteadOfFlatteningToFirstPolygon() {
        val feature = S57Feature(
            id = 42,
            objectClass = "DEPARE",
            attributes = mapOf("DRVAL1" to S57Value.Text("0"), "DRVAL2" to S57Value.Text("10")),
            geometry = S57Geometry.MultiPolygon(
                listOf(
                    polygon(-74.0, 40.0),
                    polygon(-73.8, 40.2)
                )
            )
        )

        val result = S57ToS52Adapter().adaptFeature(feature)
        assertEquals(2, result.features.size)
        assertEquals(listOf(42001L, 42002L), result.features.map { it.id })
        assertTrue(result.features.all { it.sourceFeatureId == 42L })
        assertTrue(result.features.all { it.primitive == S57PortrayalPrimitive.Area })
        assertTrue(result.features.all { it.geometry is S57PortrayalGeometry.Polygon })
        assertTrue(result.diagnostics.any { "split into 2" in it.message })
    }

    @Test
    fun splitsSoundingMultipointAndKeepsPerPointDepths() {
        val feature = S57Feature(
            id = 129,
            objectClass = "SOUNDG",
            attributes = mapOf("VALSOU" to S57Value.ListValue(listOf(S57Value.Decimal(3.1), S57Value.Decimal(4.2), S57Value.Decimal(5.3)))),
            geometry = S57Geometry.MultiPoint(listOf(
                GeoPoint(-74.1, 40.1),
                GeoPoint(-74.0, 40.2),
                GeoPoint(-73.9, 40.3)
            ))
        )

        val result = S57ToS52Adapter().adaptFeature(feature)
        assertEquals(3, result.features.size)
        assertTrue(result.features.all { it.objectClassAcronym == "SOUNDG" })
        assertTrue(result.features.all { it.primitive == S57PortrayalPrimitive.Point })
        assertEquals(S57PortrayalValue.Decimal(3.1), result.features[0].attributes["VALSOU"])
        assertEquals(S57PortrayalValue.Decimal(4.2), result.features[1].attributes["VALSOU"])
        assertEquals(S57PortrayalValue.Decimal(5.3), result.features[2].attributes["VALSOU"])
    }

    @Test
    fun preservesUnknownObjectClassesForFallbackPortrayalWhileReportingThem() {
        val features = listOf(
            S57Feature(
                id = 1,
                objectClass = "OBJL_9999",
                geometry = S57Geometry.Point(GeoPoint(-74.0, 40.0))
            ),
            S57Feature(
                id = 2,
                objectClass = "BOYLAT",
                attributes = mapOf("ATTL_9999" to S57Value.Text("ignored")),
                geometry = S57Geometry.Point(GeoPoint(-74.1, 40.1))
            )
        )

        val result = S57ToS52Adapter().adaptFeatures(features)
        assertEquals(2, result.features.size)
        assertEquals(listOf("OBJL_9999", "BOYLAT"), result.features.map { it.objectClassAcronym })
        assertEquals(1, result.unsupportedObjectClassCount)
        assertEquals(1, result.unsupportedAttributeCount)
    }

    @Test
    fun mapsExpectedObjectClassesToDistinctPrimitiveTypes() {
        val result = S57ToS52Adapter().adaptFeatures(
            listOf(
                S57Feature(id = 1, objectClass = "DEPARE", geometry = polygon(-74.0, 40.0)),
                S57Feature(id = 2, objectClass = "DEPCNT", geometry = S57Geometry.LineString(listOf(GeoPoint(-74.0, 40.0), GeoPoint(-73.9, 40.1)))),
                S57Feature(id = 3, objectClass = "SOUNDG", attributes = mapOf("VALSOU" to S57Value.Decimal(4.2)), geometry = S57Geometry.Point(GeoPoint(-74.1, 40.2))),
                S57Feature(id = 4, objectClass = "BOYLAT", geometry = S57Geometry.Point(GeoPoint(-74.2, 40.3)))
            )
        )

        assertEquals(listOf("DEPARE", "DEPCNT", "SOUNDG", "BOYLAT"), result.features.map { it.objectClassAcronym })
        assertEquals(listOf(S57PortrayalPrimitive.Area, S57PortrayalPrimitive.Line, S57PortrayalPrimitive.Point, S57PortrayalPrimitive.Point), result.features.map { it.primitive })
    }

    private fun polygon(lon: Double, lat: Double): S57Geometry.Polygon = S57Geometry.Polygon(
        listOf(
            listOf(
                GeoPoint(lon, lat),
                GeoPoint(lon + 0.1, lat),
                GeoPoint(lon + 0.1, lat + 0.1),
                GeoPoint(lon, lat)
            )
        )
    )
}
