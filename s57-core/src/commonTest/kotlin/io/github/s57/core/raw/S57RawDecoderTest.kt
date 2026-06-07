package io.github.s57.core.raw

import io.github.s57.iso8211.Iso8211Reader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class S57RawDecoderTest {
    @Test
    fun decodesDatasetMetadataFeatureAndVectorRecords() {
        val bytes = buildRecord(
            listOf(
                "DSID" to "DSNM=US5TEST;EDTN=3;UPDN=0;ISDT=20260101".bytesWithFieldTerminator(),
                "DSSI" to "COMF=10000000;SOMF=10".bytesWithFieldTerminator()
            )
        ) + buildRecord(
            listOf(
                "FRID" to frid(rcnm = 100, rcid = 7, prim = 3, grup = 2, objl = 42, rver = 1, ruin = 1).withFieldTerminator(),
                "FOID" to foid(agency = 550, fidn = 123456, fids = 1).withFieldTerminator(),
                "ATTF" to attributes(87 to "3.5", 88 to "8.0", 116 to "Test depth area"),
                "FSPT" to fspt(rcnm = 130, rcid = 88, ornt = 1, usag = 1, mask = 255).withFieldTerminator()
            )
        ) + buildRecord(
            listOf(
                "VRID" to vrid(rcnm = 130, rcid = 88, rver = 1, ruin = 1).withFieldTerminator(),
                "SG2D" to ByteArray(16).withFieldTerminator()
            )
        )

        val dataset = S57RawDecoder().decode(bytes)
        assertEquals("US5TEST", dataset.metadata.cellName)
        assertEquals(3, dataset.metadata.edition)
        assertEquals(0, dataset.metadata.updateNumber)
        assertEquals(10_000_000, dataset.metadata.coordinateMultiplier)
        assertEquals(1, dataset.features.size)
        assertEquals(1, dataset.vectors.size)

        val feature = dataset.features.single()
        assertEquals(7, feature.id)
        assertEquals(S57Primitive.Area, feature.primitive)
        assertEquals(42, feature.objectClassCode)
        assertEquals("DEPARE", feature.objectClassAcronym)
        assertEquals(S57UpdateInstruction.Insert, feature.updateInstruction)
        assertEquals(550, feature.featureObjectId?.agency)
        assertEquals("DRVAL1", feature.attributes[0].acronym)
        assertEquals("3.5", feature.attributes[0].value)
        assertEquals("OBJNAM", feature.attributes[2].acronym)
        assertEquals(1, feature.spatialReferences.size)
        assertEquals(88, feature.spatialReferences.single().name.recordId)

        val vector = dataset.vectors.single()
        assertEquals(88, vector.id)
        assertEquals(2, vector.twoDimensionalCoordinateCount)
        assertEquals(0, vector.threeDimensionalCoordinateCount)
        assertEquals(2, vector.twoDimensionalCoordinates.size)
    }

    @Test
    fun countsFeaturesByObjectClassAndBuildsFeatureStubs() {
        val bytes = buildRecord(listOf("FRID" to frid(100, 1, 3, 1, 42, 1, 1).withFieldTerminator())) +
            buildRecord(listOf("FRID" to frid(100, 2, 2, 1, 43, 1, 1).withFieldTerminator())) +
            buildRecord(listOf("FRID" to frid(100, 3, 1, 1, 129, 1, 1).withFieldTerminator()))

        val dataset = S57RawDecoder().decode(bytes)
        assertEquals(mapOf("DEPARE" to 1, "DEPCNT" to 1, "SOUNDG" to 1), dataset.featureCountsByObjectClass())
        val stubs = dataset.toFeatureStubs()
        assertEquals(listOf("DEPARE", "DEPCNT", "SOUNDG"), stubs.map { it.objectClass })
    }

    @Test
    fun supportsSimpleTextFixturesForDiagnostics() {
        val bytes = buildRecord(
            listOf(
                "FRID" to "RCID=99;OBJL=86;PRIM=1;ACRONYM=OBSTRN".bytesWithFieldTerminator(),
                "ATTF" to "OBJNAM=Rock;VALSOU=1.2".bytesWithFieldTerminator()
            )
        )
        val dataset = S57RawDecoder().decode(bytes)
        val feature = dataset.features.single()
        assertEquals("OBSTRN", feature.objectClassAcronym)
        assertEquals("Rock", feature.attributes.first { it.acronym == "OBJNAM" }.value)
        assertTrue("OBSTRN=1" in S57RawDumper.summarize(dataset))
    }

    private fun String.bytesWithFieldTerminator(): ByteArray = encodeToByteArray() + byteArrayOf(Iso8211Reader.FIELD_TERMINATOR_BYTE)
    private fun ByteArray.withFieldTerminator(): ByteArray = this + byteArrayOf(Iso8211Reader.FIELD_TERMINATOR_BYTE)

    private fun frid(rcnm: Int, rcid: Long, prim: Int, grup: Int, objl: Int, rver: Int, ruin: Int): ByteArray =
        byteArrayOf(rcnm.toByte()) + u32(rcid) + byteArrayOf(prim.toByte(), grup.toByte()) + u16(objl) + u16(rver) + byteArrayOf(ruin.toByte())

    private fun vrid(rcnm: Int, rcid: Long, rver: Int, ruin: Int): ByteArray =
        byteArrayOf(rcnm.toByte()) + u32(rcid) + u16(rver) + byteArrayOf(ruin.toByte())

    private fun foid(agency: Int, fidn: Long, fids: Int): ByteArray = u16(agency) + u32(fidn) + u16(fids)

    private fun attributes(vararg values: Pair<Int, String>): ByteArray {
        val out = mutableListOf<Byte>()
        values.forEachIndexed { index, (code, value) ->
            u16(code).forEach(out::add)
            value.encodeToByteArray().forEach(out::add)
            out += if (index == values.lastIndex) Iso8211Reader.FIELD_TERMINATOR_BYTE else Iso8211Reader.UNIT_TERMINATOR_BYTE
        }
        return out.toByteArray()
    }

    private fun fspt(rcnm: Int, rcid: Long, ornt: Int, usag: Int, mask: Int): ByteArray =
        byteArrayOf(rcnm.toByte()) + u32(rcid) + byteArrayOf(ornt.toByte(), usag.toByte(), mask.toByte())

    private fun u16(value: Int): ByteArray = byteArrayOf((value and 0xff).toByte(), ((value ushr 8) and 0xff).toByte())
    private fun u32(value: Long): ByteArray = byteArrayOf(
        (value and 0xff).toByte(),
        ((value ushr 8) and 0xff).toByte(),
        ((value ushr 16) and 0xff).toByte(),
        ((value ushr 24) and 0xff).toByte()
    )

    private fun buildRecord(fields: List<Pair<String, ByteArray>>): ByteArray {
        val tagSize = 4
        val lengthSize = 4
        val positionSize = 5
        var position = 0
        val directory = StringBuilder()
        val fieldArea = mutableListOf<Byte>()
        for ((tag, data) in fields) {
            require(tag.length == tagSize)
            directory.append(tag)
            directory.append(data.size.toString().padStart(lengthSize, '0'))
            directory.append(position.toString().padStart(positionSize, '0'))
            data.forEach { fieldArea += it }
            position += data.size
        }
        val baseAddress = io.github.s57.iso8211.Iso8211Leader.LENGTH + directory.length + 1
        val recordLength = baseAddress + fieldArea.size + 1
        val leader = buildString {
            append(recordLength.toString().padStart(5, '0'))
            append('3')
            append('D')
            append('L')
            append('1')
            append(' ')
            append("00")
            append(baseAddress.toString().padStart(5, '0'))
            append("   ")
            append(lengthSize)
            append(positionSize)
            append('0')
            append(tagSize)
        }
        check(leader.length == io.github.s57.iso8211.Iso8211Leader.LENGTH)
        return leader.encodeToByteArray() +
            directory.toString().encodeToByteArray() +
            byteArrayOf(Iso8211Reader.FIELD_TERMINATOR_BYTE) +
            fieldArea.toByteArray() +
            byteArrayOf(Iso8211Reader.FIELD_TERMINATOR_BYTE)
    }
}
