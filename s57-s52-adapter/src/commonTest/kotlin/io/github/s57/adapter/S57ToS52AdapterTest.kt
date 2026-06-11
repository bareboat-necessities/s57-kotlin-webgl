package io.github.s57.adapter

import io.github.s57.core.GeoPoint
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import io.github.s57.core.S57Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class S57ToS52AdapterTest {
    @Test
    fun preservesObjectClassAcronymInCompatibilitySummary() {
        val adapted = S57ToS52Adapter().adapt(S57Feature(id = 7, objectClass = "DEPARE"))
        assertEquals("DEPARE", adapted.objectClassAcronym)
    }

    @Test
    fun convertsAreaFeatureToS52ShapedPortrayalFeature() {
        val feature = S57Feature(
            id = 42,
            objectClass = "DEPARE",
            attributes = mapOf(
                "DRVAL1" to S57Value.Text("3.5"),
                "DRVAL2" to S57Value.Decimal(8.0),
                "OBJNAM" to S57Value.Text("Test depth area"),
                "SCAMIN" to S57Value.Integer(22000)
            ),
            geometry = S57Geometry.Polygon(
                listOf(
                    listOf(
                        GeoPoint(-74.0, 40.0),
                        GeoPoint(-73.9, 40.0),
                        GeoPoint(-73.9, 40.1),
                        GeoPoint(-74.0, 40.0)
                    )
                )
            )
        )

        val result = S57ToS52Adapter().adaptFeature(feature)
        assertTrue(result.diagnostics.none { it.severity == S57ToS52DiagnosticSeverity.Warning })
        val enc = result.features.single()
        assertEquals("DEPARE", enc.objectClassAcronym)
        assertEquals(S57PortrayalPrimitive.Area, enc.primitive)
        assertTrue(enc.geometry is S57PortrayalGeometry.Polygon)
        assertEquals(22000, enc.scaleMin)
        assertEquals(S57PortrayalValue.Decimal(3.5), enc.attributes["DRVAL1"])
        assertEquals(S57PortrayalValue.Decimal(8.0), enc.attributes["DRVAL2"])
        assertEquals(S57PortrayalValue.Text("Test depth area"), enc.attributes["OBJNAM"])
    }

    @Test
    fun convertsSoundingMultiPointWithDepthAttribute() {
        val feature = S57Feature(
            id = 129,
            objectClass = "SOUNDG",
            attributes = mapOf("VALSOU" to S57Value.Text("4.2;5.1")),
            geometry = S57Geometry.MultiPoint(listOf(GeoPoint(-74.1, 40.2), GeoPoint(-74.0, 40.25)))
        )

        val result = S57ToS52Adapter().adaptFeature(feature)
        assertEquals(2, result.features.size)
        assertEquals(listOf(129001L, 129002L), result.features.map { it.id })
        assertTrue(result.features.all { it.sourceFeatureId == 129L })
        assertTrue(result.features.all { it.objectClassAcronym == "SOUNDG" })
        assertTrue(result.features.all { it.primitive == S57PortrayalPrimitive.Point })
        assertTrue(result.features.all { it.geometry is S57PortrayalGeometry.Point })
        assertEquals(S57PortrayalValue.Decimal(4.2), result.features[0].attributes["VALSOU"])
        assertEquals(S57PortrayalValue.Decimal(5.1), result.features[1].attributes["VALSOU"])
    }

    @Test
    fun parsesS57EnumerationsAndEnumerationListsForOpenCpnPortrayal() {
        val feature = S57Feature(
            id = 17,
            objectClass = "BOYLAT",
            attributes = mapOf(
                "BOYSHP" to S57Value.Text("2"),
                "COLOUR" to S57Value.Text("3,4"),
                "TOPSHP" to S57Value.Text("11")
            ),
            geometry = S57Geometry.Point(GeoPoint(-74.0, 40.0))
        )

        val result = S57ToS52Adapter().adaptFeature(feature)
        val enc = result.features.single()
        assertEquals(S57PortrayalValue.Integer(2), enc.attributes["BOYSHP"])
        assertEquals(
            S57PortrayalValue.ListValue(listOf(S57PortrayalValue.Integer(3), S57PortrayalValue.Integer(4))),
            enc.attributes["COLOUR"]
        )
        assertEquals(S57PortrayalValue.Integer(11), enc.attributes["TOPSHP"])
    }

    @Test
    fun reportsEmptyGeometryInsteadOfPretendingItCanBePortrayed() {
        val feature = S57Feature(
            id = 999,
            objectClass = "DEPARE",
            geometry = S57Geometry.Empty
        )
        val result = S57ToS52Adapter().adaptFeature(feature)
        assertTrue(result.features.isEmpty())
        assertNotNull(result.diagnostics.firstOrNull { "no renderable geometry" in it.message })
    }
}
