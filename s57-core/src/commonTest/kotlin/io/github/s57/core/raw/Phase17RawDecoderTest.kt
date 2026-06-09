package io.github.s57.core.raw

import io.github.s57.iso8211.Iso8211Reader
import kotlin.test.Test
import kotlin.test.assertEquals

class Phase17RawDecoderTest {
    @Test
    fun decodesVrptVectorReferences() {
        val bytes = buildRecord(
            listOf(
                "VRID" to vrid(rcnm = 130, rcid = 88, rver = 1, ruin = 1).withFieldTerminator(),
                "VRPT" to vrpt(120, 10, 1, 1, 1, 255, 120, 11, 1, 2, 1, 255).withFieldTerminator()
            )
        )

        val vector = S57RawDecoder().decode(bytes).vectors.single()
        assertEquals(2, vector.vectorReferences.size)
        assertEquals(10, vector.vectorReferences[0].name.recordId)
        assertEquals(11, vector.vectorReferences[1].name.recordId)
        assertEquals(2, vector.vectorReferences[1].usage)
    }

    private fun ByteArray.withFieldTerminator(): ByteArray = this + byteArrayOf(Iso8211Reader.FIELD_TERMINATOR_BYTE)

    private fun vrid(rcnm: Int, rcid: Long, rver: Int, ruin: Int): ByteArray =
        byteArrayOf(rcnm.toByte()) + u32(rcid) + u16(rver) + byteArrayOf(ruin.toByte())

    private fun vrpt(
        rcnm1: Int,
        rcid1: Long,
        ornt1: Int,
        usag1: Int,
        topi1: Int,
        mask1: Int,
        rcnm2: Int,
        rcid2: Long,
        ornt2: Int,
        usag2: Int,
        topi2: Int,
        mask2: Int
    ): ByteArray =
        byteArrayOf(rcnm1.toByte()) + u32(rcid1) + byteArrayOf(ornt1.toByte(), usag1.toByte(), topi1.toByte(), mask1.toByte()) +
            byteArrayOf(rcnm2.toByte()) + u32(rcid2) + byteArrayOf(ornt2.toByte(), usag2.toByte(), topi2.toByte(), mask2.toByte())

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
