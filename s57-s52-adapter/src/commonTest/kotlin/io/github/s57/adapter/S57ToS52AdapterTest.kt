package io.github.s57.adapter

import io.github.s52.catalog.PrimitiveType
import io.github.s52.catalog.S57Attribute
import io.github.s52.catalog.S57ObjectClass
import io.github.s52.core.geometry.EncGeometry
import io.github.s52.core.model.S57Value as S52Value
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
    fun convertsAreaFeatureToActualS52EncFeature() {
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
        assertEquals(S57ObjectClass.DEPARE, enc.objectClass)
        assertEquals(PrimitiveType.Area, enc.primitive)
        assertTrue(enc.geometry is EncGeometry.Polygon)
        assertEquals(22000, enc.scaleMin)
        assertEquals(S52Value.Text("3.5"), enc.attributes.asMap()[S57Attribute.DRVAL1])
        assertEquals(S52Value.Decimal(8.0), enc.attributes.asMap()[S57Attribute.DRVAL2])
        assertEquals(S52Value.Text("Test depth area"), enc.attributes.asMap()[S57Attribute.OBJNAM])
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
        assertEquals(S57ObjectClass.SOUNDG, enc.objectClass)
        assertEquals(PrimitiveType.Point, enc.primitive)
        assertTrue(enc.geometry is EncGeometry.MultiPoint)
        assertEquals(S52Value.Text("4.2"), enc.attributes.asMap()[S57Attribute.VALSOU])
    }

    @Test
    fun reportsUnsupportedObjectClassesInsteadOfPretendingTheyCanBePortrayed() {
        val feature = S57Feature(
            id = 999,
            objectClass = "NOTYET",
            geometry = S57Geometry.Point(GeoPoint(-74.0, 40.0))
        )
        val result = S57ToS52Adapter().adaptFeature(feature)
        assertTrue(result.features.isEmpty())
        assertNotNull(result.diagnostics.firstOrNull { "Unsupported S-57 object class" in it.message })
    }

    @Test
    fun realS52PortrayalProducesDrawCommandsForRepresentativeFeatures() {
        val features = listOf(
            S57Feature(
                id = 1,
                objectClass = "DEPARE",
                attributes = mapOf("DRVAL1" to S57Value.Decimal(0.0), "DRVAL2" to S57Value.Decimal(4.0)),
                geometry = S57Geometry.Polygon(listOf(listOf(GeoPoint(-74.1, 40.0), GeoPoint(-73.9, 40.0), GeoPoint(-73.9, 40.1), GeoPoint(-74.1, 40.0))))
            ),
            S57Feature(id = 2, objectClass = "DEPCNT", attributes = mapOf("VALDCO" to S57Value.Decimal(10.0)), geometry = S57Geometry.LineString(listOf(GeoPoint(-74.1, 40.05), GeoPoint(-73.9, 40.05)))),
            S57Feature(id = 3, objectClass = "BOYLAT", geometry = S57Geometry.Point(GeoPoint(-74.0, 40.07)))
        )

        val portrayed = S57ToS52Adapter().portray(features)
        assertEquals(3, portrayed.features.size)
        assertTrue(portrayed.commands.isNotEmpty(), "S-52 runtime should produce actual draw commands")
        assertTrue("commands" in portrayed.commandTranscript || portrayed.commandTranscript.isNotBlank())
    }
}
