package io.github.s57.iso8211

/**
 * Reusable ISO/IEC 8211 record reader.
 *
 * This layer intentionally knows nothing about S-57 semantics. It reads the
 * ISO8211 leader, directory entries, field byte ranges, and delimiter-separated
 * subfield payload chunks. S-57 object/attribute decoding belongs in s57-core.
 */
class Iso8211Reader {
    fun readRecords(bytes: ByteArray): List<Iso8211Record> {
        val records = mutableListOf<Iso8211Record>()
        var offset = 0
        while (offset + Iso8211Leader.LENGTH <= bytes.size) {
            val length = digits(bytes, offset, 5) ?: break
            if (length <= 0 || offset + length > bytes.size) break
            records += readRecord(bytes, offset, length)
            offset += length
        }
        return records
    }

    fun readSingleRecord(bytes: ByteArray, offset: Int = 0): Iso8211Record {
        require(offset + Iso8211Leader.LENGTH <= bytes.size) { "Not enough bytes for ISO8211 leader at offset $offset" }
        val length = digits(bytes, offset, 5) ?: error("Invalid ISO8211 record length at offset $offset")
        require(length > 0 && offset + length <= bytes.size) { "Invalid ISO8211 record length $length at offset $offset" }
        return readRecord(bytes, offset, length)
    }

    private fun readRecord(bytes: ByteArray, offset: Int, length: Int): Iso8211Record {
        val leader = parseLeader(bytes, offset)
        val directory = parseDirectory(bytes, offset, leader)
        val fields = directory.map { entry ->
            val absolute = offset + leader.baseAddressOfFieldArea + entry.fieldPosition
            require(absolute >= offset && absolute + entry.fieldLength <= offset + length) {
                "ISO8211 field ${entry.tag} points outside record: position=${entry.fieldPosition}, length=${entry.fieldLength}, recordLength=$length"
            }
            val raw = bytes.copyOfRange(absolute, absolute + entry.fieldLength)
            Iso8211Field(
                tag = entry.tag,
                data = raw,
                dataOffset = absolute,
                directoryEntry = entry,
                subfields = splitSubfields(raw)
            )
        }
        return Iso8211Record(
            leader = leader,
            directory = directory,
            fields = fields,
            rawOffset = offset,
            rawLength = length
        )
    }

    private fun parseLeader(bytes: ByteArray, offset: Int): Iso8211Leader = Iso8211Leader(
        recordLength = digits(bytes, offset, 5) ?: error("Invalid ISO8211 record length at offset $offset"),
        interchangeLevel = charAt(bytes, offset + 5),
        leaderIdentifier = charAt(bytes, offset + 6),
        inlineCodeExtensionIndicator = charAt(bytes, offset + 7),
        versionNumber = charAt(bytes, offset + 8),
        applicationIndicator = charAt(bytes, offset + 9),
        fieldControlLength = digits(bytes, offset + 10, 2) ?: 0,
        baseAddressOfFieldArea = digits(bytes, offset + 12, 5) ?: error("Invalid ISO8211 base address at offset $offset"),
        extendedCharacterSetIndicator = slice(bytes, offset + 17, 3),
        sizeOfFieldLength = digit(bytes, offset + 20) ?: 0,
        sizeOfFieldPosition = digit(bytes, offset + 21) ?: 0,
        reserved = charAt(bytes, offset + 22),
        sizeOfFieldTag = digit(bytes, offset + 23) ?: 0
    )

    private fun parseDirectory(bytes: ByteArray, recordOffset: Int, leader: Iso8211Leader): List<Iso8211DirectoryEntry> {
        val entrySize = leader.directoryEntryLength
        if (entrySize <= 0) return emptyList()
        val directoryStart = recordOffset + Iso8211Leader.LENGTH
        val directoryEndExclusive = recordOffset + leader.baseAddressOfFieldArea
        require(directoryEndExclusive > directoryStart && directoryEndExclusive <= bytes.size) {
            "Invalid ISO8211 directory bounds: start=$directoryStart end=$directoryEndExclusive"
        }

        val entries = mutableListOf<Iso8211DirectoryEntry>()
        var cursor = directoryStart
        while (cursor < directoryEndExclusive) {
            if (bytes[cursor] == FIELD_TERMINATOR) break
            if (cursor + entrySize > directoryEndExclusive) break
            val tag = slice(bytes, cursor, leader.sizeOfFieldTag)
            val fieldLength = digits(bytes, cursor + leader.sizeOfFieldTag, leader.sizeOfFieldLength)
                ?: error("Invalid ISO8211 field length for tag $tag")
            val fieldPosition = digits(bytes, cursor + leader.sizeOfFieldTag + leader.sizeOfFieldLength, leader.sizeOfFieldPosition)
                ?: error("Invalid ISO8211 field position for tag $tag")
            entries += Iso8211DirectoryEntry(tag, fieldLength, fieldPosition)
            cursor += entrySize
        }
        return entries
    }

    private fun splitSubfields(data: ByteArray): List<Iso8211Subfield> {
        val result = mutableListOf<Iso8211Subfield>()
        var start = 0
        var index = 0
        var ordinal = 0
        while (index < data.size) {
            val value = data[index]
            if (value == UNIT_TERMINATOR || value == FIELD_TERMINATOR) {
                result += Iso8211Subfield(ordinal, data.copyOfRange(start, index), value)
                ordinal++
                start = index + 1
                if (value == FIELD_TERMINATOR) return result
            }
            index++
        }
        if (start < data.size || result.isEmpty()) result += Iso8211Subfield(ordinal, data.copyOfRange(start, data.size), null)
        return result
    }

    private fun digits(bytes: ByteArray, offset: Int, count: Int): Int? {
        if (count <= 0 || offset < 0 || offset + count > bytes.size) return null
        var result = 0
        repeat(count) { index ->
            val value = digit(bytes, offset + index) ?: return null
            result = result * 10 + value
        }
        return result
    }

    private fun digit(bytes: ByteArray, index: Int): Int? {
        if (index !in bytes.indices) return null
        val c = bytes[index].toInt().toChar()
        return if (c in '0'..'9') c - '0' else null
    }

    private fun charAt(bytes: ByteArray, index: Int): Char = if (index in bytes.indices) bytes[index].toInt().toChar() else ' '

    private fun slice(bytes: ByteArray, offset: Int, count: Int): String = buildString {
        for (i in 0 until count) if (offset + i in bytes.indices) append(bytes[offset + i].toInt().toChar())
    }

    companion object {
        const val FIELD_TERMINATOR_BYTE: Byte = 0x1E
        const val UNIT_TERMINATOR_BYTE: Byte = 0x1F
        private const val FIELD_TERMINATOR: Byte = FIELD_TERMINATOR_BYTE
        private const val UNIT_TERMINATOR: Byte = UNIT_TERMINATOR_BYTE
    }
}

data class Iso8211Record(
    val leader: Iso8211Leader,
    val directory: List<Iso8211DirectoryEntry>,
    val fields: List<Iso8211Field>,
    val rawOffset: Int,
    val rawLength: Int
) {
    fun field(tag: String): Iso8211Field? = fields.firstOrNull { it.tag == tag }
    fun fields(tag: String): List<Iso8211Field> = fields.filter { it.tag == tag }
}

data class Iso8211Leader(
    val recordLength: Int,
    val interchangeLevel: Char,
    val leaderIdentifier: Char,
    val inlineCodeExtensionIndicator: Char,
    val versionNumber: Char,
    val applicationIndicator: Char,
    val fieldControlLength: Int,
    val baseAddressOfFieldArea: Int,
    val extendedCharacterSetIndicator: String,
    val sizeOfFieldLength: Int,
    val sizeOfFieldPosition: Int,
    val reserved: Char,
    val sizeOfFieldTag: Int
) {
    val directoryEntryLength: Int get() = sizeOfFieldTag + sizeOfFieldLength + sizeOfFieldPosition

    companion object {
        const val LENGTH: Int = 24
    }
}

data class Iso8211DirectoryEntry(
    val tag: String,
    val fieldLength: Int,
    val fieldPosition: Int
)

data class Iso8211Field(
    val tag: String,
    val data: ByteArray,
    val dataOffset: Int,
    val directoryEntry: Iso8211DirectoryEntry,
    val subfields: List<Iso8211Subfield>
) {
    val content: ByteArray
        get() = data.dropLastTerminator()

    fun text(): String = content.decodeToString()

    override fun equals(other: Any?): Boolean = other is Iso8211Field &&
        tag == other.tag &&
        data.contentEquals(other.data) &&
        dataOffset == other.dataOffset &&
        directoryEntry == other.directoryEntry &&
        subfields == other.subfields

    override fun hashCode(): Int {
        var result = tag.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + dataOffset
        result = 31 * result + directoryEntry.hashCode()
        result = 31 * result + subfields.hashCode()
        return result
    }
}

data class Iso8211Subfield(
    val ordinal: Int,
    val data: ByteArray,
    val terminator: Byte?
) {
    fun text(): String = data.decodeToString()

    override fun equals(other: Any?): Boolean = other is Iso8211Subfield &&
        ordinal == other.ordinal &&
        data.contentEquals(other.data) &&
        terminator == other.terminator

    override fun hashCode(): Int {
        var result = ordinal
        result = 31 * result + data.contentHashCode()
        result = 31 * result + (terminator?.hashCode() ?: 0)
        return result
    }
}

private fun ByteArray.dropLastTerminator(): ByteArray =
    if (isNotEmpty() && (last() == Iso8211Reader.FIELD_TERMINATOR_BYTE || last() == Iso8211Reader.UNIT_TERMINATOR_BYTE)) {
        copyOfRange(0, size - 1)
    } else {
        this
    }
