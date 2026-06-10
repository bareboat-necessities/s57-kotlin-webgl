package io.github.s57.iso8211

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Iso8211ReaderTest {
    @Test
    fun readsMinimalRecordLengthFromLeader() {
        val bytes = buildRecord(emptyList())
        val records = Iso8211Reader().readRecords(bytes)
        assertEquals(1, records.size)
        assertEquals(26, records.first().leader.recordLength)
        assertEquals(25, records.first().leader.baseAddressOfFieldArea)
        assertTrue(records.first().fields.isEmpty())
    }

    @Test
    fun parsesDirectoryEntriesAndFieldPayloads() {
        val bytes = buildRecord(
            listOf(
                "DSID" to "CELL_A".bytesWithFieldTerminator(),
                "DSSI" to "A${Iso8211Reader.UNIT_TERMINATOR_BYTE.toInt().toChar()}B".encodeToByteArray().plus(byteArrayOf(Iso8211Reader.FIELD_TERMINATOR_BYTE))
            )
        )

        val record = Iso8211Reader().readSingleRecord(bytes)
        assertEquals(2, record.directory.size)
        assertEquals(listOf("DSID", "DSSI"), record.fields.map { it.tag })
        assertEquals("CELL_A", record.field("DSID")?.text())
        assertEquals(2, record.field("DSSI")?.subfields?.size)
        assertEquals("A", record.field("DSSI")?.subfields?.get(0)?.text())
        assertEquals("B", record.field("DSSI")?.subfields?.get(1)?.text())
    }

    @Test
    fun readsMultipleRecordsInOneByteStream() {
        val first = buildRecord(listOf("0001" to "ONE".bytesWithFieldTerminator()))
        val second = buildRecord(listOf("0002" to "TWO".bytesWithFieldTerminator()))
        val records = Iso8211Reader().readRecords(first + second)
        assertEquals(2, records.size)
        assertEquals("ONE", records[0].field("0001")?.text())
        assertEquals("TWO", records[1].field("0002")?.text())
        assertEquals(first.size, records[1].rawOffset)
    }

    @Test
    fun dumpSummarizesTagsAndSubfieldCounts() {
        val record = Iso8211Reader().readSingleRecord(buildRecord(listOf("DSID" to "CELL_A".bytesWithFieldTerminator())))
        val dump = Iso8211RecordDumper.summarize(listOf(record))
        assertTrue("ISO8211 records=1" in dump)
        assertTrue("DSID" in dump)
        assertTrue("CELL_A" in dump)
    }

    @Test
    fun exposesFieldLookupByTag() {
        val record = Iso8211Reader().readSingleRecord(buildRecord(listOf("TAG1" to "VALUE".bytesWithFieldTerminator())))
        assertNotNull(record.field("TAG1"))
        assertEquals(1, record.fields("TAG1").size)
    }

    private fun String.bytesWithFieldTerminator(): ByteArray = encodeToByteArray() + byteArrayOf(Iso8211Reader.FIELD_TERMINATOR_BYTE)

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
            append('3') // interchange level
            append('D') // data record
            append('L')
            append('1')
            append(' ') // application indicator
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
