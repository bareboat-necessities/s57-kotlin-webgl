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
    fun convertsAreaFeatureToPortrayalFeature() {
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
        assertEquals(3.5, enc.attributes["DRVAL1"]?.asDoubleOrNull())
        assertEquals(8.0, enc.attributes["DRVAL2"]?.asDoubleOrNull())
        assertEquals("Test depth area", enc.attributes["OBJNAM"]?.asTextOrNull())
    }

    @Test
    fun convertsSoundingMultiPointWithDepthAttribute() {
        val feature = S57Feature(
            id = 129,
            objectClass = "SOUNDG",
            attributes = mapOf("VALSOU" to S57Value.Text("4.2")),
            geometry = S57Geometry.MultiPoint(listOf(GeoPoint(-74.1, 40.2), GeoPoint(-74.0, 40.25)))
        )

        val enc = S57ToS52Adapter().adaptFeature(feature).features.single()
        assertEquals("SOUNDG", enc.objectClassAcronym)
        assertEquals(S57PortrayalPrimitive.Point, enc.primitive)
        assertTrue(enc.geometry is S57PortrayalGeometry.MultiPoint)
        assertEquals(4.2, enc.attributes["VALSOU"]?.asDoubleOrNull())
    }

    @Test
    fun preservesUnknownObjectClassesDynamicallyWithInfoDiagnostic() {
        val feature = S57Feature(
            id = 999,
            objectClass = "NOTYET",
            geometry = S57Geometry.Point(GeoPoint(-74.0, 40.0))
        )
        val result = S57ToS52Adapter().adaptFeature(feature)
        assertEquals("NOTYET", result.features.single().objectClassAcronym)
        assertNotNull(result.diagnostics.firstOrNull { "preserving acronym dynamically" in it.message })
    }

    @Test
    fun exposesPlainTranscriptWithoutS52RuntimeDependency() {
        val feature = S57Feature(id = 1, objectClass = "BOYLAT", geometry = S57Geometry.Point(GeoPoint(-74.0, 40.0)))
        val result = S57ToS52Adapter().adaptFeature(feature)
        val transcript = S57ToS52Adapter().transcript(result)
        assertTrue("BOYLAT" in transcript)
        assertTrue("features=1" in transcript)
    }
}
