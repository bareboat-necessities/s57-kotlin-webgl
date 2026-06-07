package io.github.s57.iso8211

import kotlin.test.Test
import kotlin.test.assertEquals

class Iso8211ReaderTest {
    @Test
    fun readsMinimalRecordLengthFromLeader() {
        val bytes = "00024     0000000000000".encodeToByteArray()
        val records = Iso8211Reader().readRecords(bytes)
        assertEquals(1, records.size)
        assertEquals(24, records.first().leader.recordLength)
    }
}
