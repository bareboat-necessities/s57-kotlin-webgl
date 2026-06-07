package io.github.s57.core.geometry

import io.github.s57.core.S57Geometry
import io.github.s57.core.raw.S57DatasetMetadata
import io.github.s57.core.raw.S57Primitive
import io.github.s57.core.raw.S57RawCoordinate
import io.github.s57.core.raw.S57RawDataset
import io.github.s57.core.raw.S57RawFeatureRecord
import io.github.s57.core.raw.S57RawVectorRecord
import io.github.s57.core.raw.S57RecordName
import io.github.s57.core.raw.S57SpatialReference
import io.github.s57.core.raw.S57UpdateInstruction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class S57GeometryBuilderTest {
    @Test
    fun reconstructsPointLineAndAreaGeometriesFromRawVectors() {
        val raw = S57RawDataset(
            metadata = S57DatasetMetadata(cellName = "US5TEST", coordinateMultiplier = 10_000_000),
            vectors = listOf(
                vector(10, listOf(rawPoint(-74.0000000, 40.7000000))),
                vector(20, listOf(rawPoint(-74.0000000, 40.7000000), rawPoint(-73.9900000, 40.7100000))),
                vector(30, listOf(rawPoint(-74.0, 40.7), rawPoint(-73.9, 40.7), rawPoint(-73.9, 40.8), rawPoint(-74.0, 40.8)))
            ),
            features = listOf(
                feature(1, "BOYLAT", S57Primitive.Point, 10),
                feature(2, "DEPCNT", S57Primitive.Line, 20),
                feature(3, "DEPARE", S57Primitive.Area, 30)
            ),
            unknownRecords = emptyList()
        )

        val result = S57GeometryBuilder().build(raw)
        assertEquals(3, result.features.size)
        assertTrue(result.features[0].geometry is S57Geometry.Point)
        assertTrue(result.features[1].geometry is S57Geometry.LineString)
        val polygon = result.features[2].geometry as S57Geometry.Polygon
        assertEquals(result.features[2].bounds, result.dataset.summary.bounds)
        assertEquals(polygon.rings.first().first(), polygon.rings.first().last())
        assertEquals(0, result.report().emptyCount)
    }

    @Test
    fun reverseOrientationReversesLineSegments() {
        val raw = S57RawDataset(
            metadata = S57DatasetMetadata(cellName = "US5TEST", coordinateMultiplier = 10_000_000),
            vectors = listOf(vector(20, listOf(rawPoint(-74.0, 40.7), rawPoint(-73.9, 40.8)))),
            features = listOf(feature(2, "DEPCNT", S57Primitive.Line, 20, orientation = 2)),
            unknownRecords = emptyList()
        )
        val line = S57GeometryBuilder().build(raw).features.single().geometry as S57Geometry.LineString
        assertEquals(-73.9, line.points.first().lon)
        assertEquals(40.8, line.points.first().lat)
    }

    @Test
    fun reportsMissingVectorsWithoutDroppingFeature() {
        val raw = S57RawDataset(
            metadata = S57DatasetMetadata(cellName = "US5TEST"),
            vectors = emptyList(),
            features = listOf(feature(9, "WRECKS", S57Primitive.Point, 999)),
            unknownRecords = emptyList()
        )
        val result = S57GeometryBuilder().build(raw)
        assertTrue(result.features.single().geometry is S57Geometry.Empty)
        assertTrue(result.diagnostics.any { "Missing vector" in it.message })
    }

    private fun rawPoint(lon: Double, lat: Double): S57RawCoordinate = S57RawCoordinate(
        yRaw = (lat * 10_000_000).toLong(),
        xRaw = (lon * 10_000_000).toLong()
    )

    private fun vector(id: Long, coords: List<S57RawCoordinate>) = S57RawVectorRecord(
        id = id,
        recordName = S57RecordName(130, id),
        version = 1,
        updateInstruction = S57UpdateInstruction.Insert,
        twoDimensionalCoordinates = coords
    )

    private fun feature(id: Long, acronym: String, primitive: S57Primitive, vectorId: Long, orientation: Int = 1) = S57RawFeatureRecord(
        id = id,
        recordName = S57RecordName(100, id),
        primitive = primitive,
        group = 1,
        objectClassCode = 0,
        objectClassAcronym = acronym,
        version = 1,
        updateInstruction = S57UpdateInstruction.Insert,
        spatialReferences = listOf(S57SpatialReference(S57RecordName(130, vectorId), orientation, usage = 1, mask = 255))
    )
}
