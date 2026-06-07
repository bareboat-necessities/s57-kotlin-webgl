package io.github.s57.core.import

import io.github.s57.core.S57Geometry
import io.github.s57.iso8211.Iso8211Leader
import io.github.s57.iso8211.Iso8211Reader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class S57ImportPipelineTest {
    @Test
    fun importsBytesThroughIso8211RawDecoderAndGeometryBuilder() {
        val bytes = buildRecord(
            listOf(
                "DSID" to "DSNM=US5PIPE;EDTN=1;UPDN=0".bytesWithFieldTerminator(),
                "DSSI" to "COMF=10000000;SOMF=10".bytesWithFieldTerminator()
            )
        ) + buildRecord(
            listOf(
                "FRID" to frid(rcnm = 100, rcid = 10, prim = 2, grup = 1, objl = 43, rver = 1, ruin = 1).withFieldTerminator(),
                "FSPT" to fspt(rcnm = 130, rcid = 20, ornt = 1, usag = 1, mask = 255).withFieldTerminator()
            )
        ) + buildRecord(
            listOf(
                "VRID" to vrid(rcnm = 130, rcid = 20, rver = 1, ruin = 1).withFieldTerminator(),
                "SG2D" to sg2d(-74.0, 40.0, -73.9, 40.1).withFieldTerminator()
            )
        )

        val imported = S57ImportPipeline().importBytes(bytes)
        assertEquals("US5PIPE", imported.dataset.summary.cellId)
        assertEquals(1, imported.featureCount)
        assertEquals(1, imported.raw.vectors.size)
        val feature = imported.dataset.features.single()
        assertEquals("DEPCNT", feature.objectClass)
        assertTrue(feature.geometry is S57Geometry.LineString)
        assertTrue(imported.toPlainText().contains("vectors=1"))
    }

    private fun String.bytesWithFieldTerminator(): ByteArray = encodeToByteArray() + byteArrayOf(Iso8211Reader.FIELD_TERMINATOR_BYTE)
    private fun ByteArray.withFieldTerminator(): ByteArray = this + byteArrayOf(Iso8211Reader.FIELD_TERMINATOR_BYTE)

    private fun frid(rcnm: Int, rcid: Long, prim: Int, grup: Int, objl: Int, rver: Int, ruin: Int): ByteArray =
        byteArrayOf(rcnm.toByte()) + u32(rcid) + byteArrayOf(prim.toByte(), grup.toByte()) + u16(objl) + u16(rver) + byteArrayOf(ruin.toByte())

    private fun vrid(rcnm: Int, rcid: Long, rver: Int, ruin: Int): ByteArray =
        byteArrayOf(rcnm.toByte()) + u32(rcid) + u16(rver) + byteArrayOf(ruin.toByte())

    private fun fspt(rcnm: Int, rcid: Long, ornt: Int, usag: Int, mask: Int): ByteArray =
        byteArrayOf(rcnm.toByte()) + u32(rcid) + byteArrayOf(ornt.toByte(), usag.toByte(), mask.toByte())

    private fun sg2d(lon1: Double, lat1: Double, lon2: Double, lat2: Double): ByteArray =
        i32((lat1 * 10_000_000).toLong()) + i32((lon1 * 10_000_000).toLong()) +
            i32((lat2 * 10_000_000).toLong()) + i32((lon2 * 10_000_000).toLong())

    private fun u16(value: Int): ByteArray = byteArrayOf((value and 0xff).toByte(), ((value ushr 8) and 0xff).toByte())
    private fun u32(value: Long): ByteArray = byteArrayOf(
        (value and 0xff).toByte(),
        ((value ushr 8) and 0xff).toByte(),
        ((value ushr 16) and 0xff).toByte(),
        ((value ushr 24) and 0xff).toByte()
    )
    private fun i32(value: Long): ByteArray = u32(value and 0xffffffffL)

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
        val baseAddress = Iso8211Leader.LENGTH + directory.length + 1
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
        check(leader.length == Iso8211Leader.LENGTH)
        return leader.encodeToByteArray() +
            directory.toString().encodeToByteArray() +
            byteArrayOf(Iso8211Reader.FIELD_TERMINATOR_BYTE) +
            fieldArea.toByteArray() +
            byteArrayOf(Iso8211Reader.FIELD_TERMINATOR_BYTE)
    }
}
