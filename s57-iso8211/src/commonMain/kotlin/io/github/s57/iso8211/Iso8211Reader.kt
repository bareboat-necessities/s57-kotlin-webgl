package io.github.s57.iso8211

/** Basic ISO/IEC 8211 record reader scaffold. Phase 1 will implement full DDR/subfield decoding. */
class Iso8211Reader {
    fun readRecords(bytes: ByteArray): List<Iso8211Record> {
        val records = mutableListOf<Iso8211Record>()
        var offset = 0
        while (offset + 24 <= bytes.size) {
            val length = parseFiveDigitLength(bytes, offset) ?: break
            if (length <= 0 || offset + length > bytes.size) break
            records += Iso8211Record(
                leader = Iso8211Leader(
                    recordLength = length,
                    interchangeLevel = charAt(bytes, offset + 5),
                    leaderIdentifier = charAt(bytes, offset + 6),
                    inlineCodeExtensionIndicator = charAt(bytes, offset + 7),
                    versionNumber = charAt(bytes, offset + 8),
                    applicationIndicator = charAt(bytes, offset + 9),
                    fieldControlLength = digits(bytes, offset + 10, 2) ?: 0,
                    baseAddressOfFieldArea = digits(bytes, offset + 12, 5) ?: 0,
                    extendedCharacterSetIndicator = slice(bytes, offset + 17, 3),
                    sizeOfFieldLength = digit(bytes, offset + 20) ?: 0,
                    sizeOfFieldPosition = digit(bytes, offset + 21) ?: 0,
                    sizeOfFieldTag = digit(bytes, offset + 23) ?: 0
                ),
                fields = emptyList(),
                rawOffset = offset,
                rawLength = length
            )
            offset += length
        }
        return records
    }

    private fun parseFiveDigitLength(bytes: ByteArray, offset: Int): Int? = digits(bytes, offset, 5)

    private fun digits(bytes: ByteArray, offset: Int, count: Int): Int? {
        if (offset < 0 || offset + count > bytes.size) return null
        var result = 0
        repeat(count) { index ->
            val value = digit(bytes, offset + index) ?: return null
            result = result * 10 + value
        }
        return result
    }

    private fun digit(bytes: ByteArray, index: Int): Int? {
        val c = bytes[index].toInt().toChar()
        return if (c in '0'..'9') c - '0' else null
    }

    private fun charAt(bytes: ByteArray, index: Int): Char = if (index in bytes.indices) bytes[index].toInt().toChar() else ' '

    private fun slice(bytes: ByteArray, offset: Int, count: Int): String = buildString {
        for (i in 0 until count) if (offset + i in bytes.indices) append(bytes[offset + i].toInt().toChar())
    }
}

data class Iso8211Record(
    val leader: Iso8211Leader,
    val fields: List<Iso8211Field>,
    val rawOffset: Int,
    val rawLength: Int
)

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
    val sizeOfFieldTag: Int
)

data class Iso8211Field(
    val tag: String,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean = other is Iso8211Field && tag == other.tag && data.contentEquals(other.data)
    override fun hashCode(): Int = 31 * tag.hashCode() + data.contentHashCode()
}
