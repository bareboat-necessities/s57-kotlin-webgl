package io.github.s57.core.raw

import io.github.s57.iso8211.Iso8211Reader
import kotlin.test.Test
import kotlin.test.assertEquals

class Phase18RawDecoderLookupTest {
    @Test
    fun rawDecoderUsesExpandedObjectLookup() {
        val bytes = listOf(
            302L to 1L,
            71L to 2L,
            30L to 3L,
            122L to 4L
        ).map { (objl, rcid) ->
            buildRecord(listOf("FRID" to frid(100, rcid, 3, 1, objl.toInt(), 1, 1).withFieldTerminator()))
        }.reduce(ByteArray::plus)

        val decoded = S57RawDecoder().decode(bytes)
        assertEquals(listOf("M_COVR", "LNDARE", "COALNE", "SLCONS"), decoded.features.map { it.objectClassAcronym })
    }

    @Test
    fun rawDecoderUsesExpandedAttributeLookup() {
        val bytes = buildRecord(
            listOf(
                "FRID" to frid(100, 9, 1, 1, 75, 1, 1).withFieldTerminator(),
                "ATTF" to attributes(187 to "4", 142 to "2.5", 158 to "note", 174 to "10.0").withFieldTerminator()
            )
        )

        val feature = S57RawDecoder().decode(bytes).features.single()
        assertEquals(listOf("WATLEV", "SIGPER", "TXTDSC", "VALDCO"), feature.attributes.map { it.acronym })
    }

    private fun ByteArray.withFieldTerminator(): ByteArray = this + byteArrayOf(Iso8211Reader.FIELD_TERMINATOR_BYTE)

    private fun frid(rcnm: Int, rcid: Long, prim: Int, grup: Int, objl: Int, rver: Int, ruin: Int): ByteArray =
        byteArrayOf(rcnm.toByte()) + u32(rcid) + byteArrayOf(prim.toByte(), grup.toByte()) + u16(objl) + u16(rver) + byteArrayOf(ruin.toByte())

    private fun attributes(vararg values: Pair<Int, String>): ByteArray {
        val out = mutableListOf<Byte>()
        values.forEachIndexed { index, (code, value) ->
            u16(code).forEach(out::add)
            value.encodeToByteArray().forEach(out::add)
            out += if (index == values.lastIndex) Iso8211Reader.FIELD_TERMINATOR_BYTE else Iso8211Reader.UNIT_TERMINATOR_BYTE
        }
        return out.toByteArray()
    }

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
