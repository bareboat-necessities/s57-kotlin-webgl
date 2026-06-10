package io.github.s57.core.geometry

import io.github.s57.core.S57Geometry
import io.github.s57.core.S57Value
import io.github.s57.core.raw.S57DatasetMetadata
import io.github.s57.core.raw.S57Primitive
import io.github.s57.core.raw.S57RawCoordinate
import io.github.s57.core.raw.S57RawDataset
import io.github.s57.core.raw.S57RawFeatureRecord
import io.github.s57.core.raw.S57RawVectorRecord
import io.github.s57.core.raw.S57RecordName
import io.github.s57.core.raw.S57SpatialReference
import io.github.s57.core.raw.S57UpdateInstruction
import io.github.s57.core.raw.S57VectorReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Phase17GeometryBuilderTest {
    @Test
    fun assemblesAreaRingFromEdgesWithConnectedNodeReferences() {
        val raw = S57RawDataset(
            metadata = S57DatasetMetadata(cellName = "US5P17", coordinateMultiplier = 10_000_000),
            vectors = listOf(
                node(1, -74.0, 40.0),
                node(2, -73.9, 40.0),
                node(3, -73.9, 40.1),
                node(4, -74.0, 40.1),
                edge(10, 1, 2),
                edge(11, 2, 3),
                edge(12, 3, 4),
                edge(13, 4, 1)
            ),
            features = listOf(areaFeature(100, listOf(10, 11, 12, 13))),
            unknownRecords = emptyList()
        )

        val result = S57GeometryBuilder().build(raw)
        val polygon = result.features.single().geometry as S57Geometry.Polygon
        val ring = polygon.rings.single()

        assertEquals(5, ring.size)
        assertEquals(ring.first(), ring.last())
        assertEquals(-74.0, ring.first().lon)
        assertEquals(40.0, ring.first().lat)
        assertTrue(result.dataset.summary.bounds != null)
        assertEquals(0, result.report().emptyCount)
    }

    @Test
    fun preservesSg3dSoundingDepthAsValsouAttribute() {
        val raw = S57RawDataset(
            metadata = S57DatasetMetadata(cellName = "US5P17", coordinateMultiplier = 10_000_000, soundingMultiplier = 10),
            vectors = listOf(
                S57RawVectorRecord(
                    id = 50,
                    recordName = S57RecordName(120, 50),
                    version = 1,
                    updateInstruction = S57UpdateInstruction.Insert,
                    threeDimensionalCoordinates = listOf(rawPoint(-74.0, 40.0, zRaw = 123))
                )
            ),
            features = listOf(pointFeature(200, "SOUNDG", 50)),
            unknownRecords = emptyList()
        )

        val feature = S57GeometryBuilder().build(raw).features.single()
        assertTrue(feature.geometry is S57Geometry.Point)
        val valsou = feature.attributes["VALSOU"] as S57Value.Decimal
        assertEquals(12.3, valsou.value)
    }


    @Test
    fun correctsPointPrimitiveWhenFeatureReferencesClosedEdgeGeometry() {
        val raw = S57RawDataset(
            metadata = S57DatasetMetadata(cellName = "US5EDGE", coordinateMultiplier = 10_000_000),
            vectors = listOf(
                node(1, -74.0, 40.0),
                node(2, -73.9, 40.0),
                node(3, -73.9, 40.1),
                node(4, -74.0, 40.1),
                edge(10, 1, 2),
                edge(11, 2, 3),
                edge(12, 3, 4),
                edge(13, 4, 1)
            ),
            features = listOf(
                areaFeature(300, listOf(10, 11, 12, 13)).copy(primitive = S57Primitive.Point)
            ),
            unknownRecords = emptyList()
        )

        val result = S57GeometryBuilder().build(raw)

        assertTrue(result.features.single().geometry is S57Geometry.Polygon)
        assertTrue(result.diagnostics.any { "Corrected primitive Point to Area" in it.message })
        assertEquals(0, result.report().pointCount)
    }

    @Test
    fun correctsPointPrimitiveWhenFeatureReferencesOpenEdgeGeometry() {
        val raw = S57RawDataset(
            metadata = S57DatasetMetadata(cellName = "US5EDGE", coordinateMultiplier = 10_000_000),
            vectors = listOf(
                node(1, -74.0, 40.0),
                node(2, -73.9, 40.0),
                edge(10, 1, 2)
            ),
            features = listOf(
                pointFeature(301, "DEPCNT", 10).copy(primitive = S57Primitive.Point, spatialReferences = listOf(S57SpatialReference(S57RecordName(130, 10), orientation = 1, usage = 1, mask = 255)))
            ),
            unknownRecords = emptyList()
        )

        val result = S57GeometryBuilder().build(raw)

        assertTrue(result.features.single().geometry is S57Geometry.LineString)
        assertTrue(result.diagnostics.any { "Corrected primitive Point to Line" in it.message })
    }

    private fun rawPoint(lon: Double, lat: Double, zRaw: Long? = null): S57RawCoordinate = S57RawCoordinate(
        yRaw = (lat * 10_000_000).toLong(),
        xRaw = (lon * 10_000_000).toLong(),
        zRaw = zRaw
    )

    private fun node(id: Long, lon: Double, lat: Double) = S57RawVectorRecord(
        id = id,
        recordName = S57RecordName(120, id),
        version = 1,
        updateInstruction = S57UpdateInstruction.Insert,
        twoDimensionalCoordinates = listOf(rawPoint(lon, lat))
    )

    private fun edge(id: Long, startNode: Long, endNode: Long) = S57RawVectorRecord(
        id = id,
        recordName = S57RecordName(130, id),
        version = 1,
        updateInstruction = S57UpdateInstruction.Insert,
        vectorReferences = listOf(
            S57VectorReference(S57RecordName(120, startNode), orientation = 1, usage = 1, topologyIndicator = 1, mask = 255),
            S57VectorReference(S57RecordName(120, endNode), orientation = 1, usage = 2, topologyIndicator = 1, mask = 255)
        )
    )

    private fun areaFeature(id: Long, edges: List<Long>) = S57RawFeatureRecord(
        id = id,
        recordName = S57RecordName(100, id),
        primitive = S57Primitive.Area,
        group = 1,
        objectClassCode = 0,
        objectClassAcronym = "DEPARE",
        version = 1,
        updateInstruction = S57UpdateInstruction.Insert,
        spatialReferences = edges.map { edgeId -> S57SpatialReference(S57RecordName(130, edgeId), orientation = 1, usage = 1, mask = 255) }
    )

    private fun pointFeature(id: Long, acronym: String, vectorId: Long) = S57RawFeatureRecord(
        id = id,
        recordName = S57RecordName(100, id),
        primitive = S57Primitive.Point,
        group = 1,
        objectClassCode = 0,
        objectClassAcronym = acronym,
        version = 1,
        updateInstruction = S57UpdateInstruction.Insert,
        spatialReferences = listOf(S57SpatialReference(S57RecordName(120, vectorId), orientation = 1, usage = 1, mask = 255))
    )
}
