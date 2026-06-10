package io.github.s57.core.raw

import io.github.s57.iso8211.Iso8211Field
import io.github.s57.iso8211.Iso8211Reader
import io.github.s57.iso8211.Iso8211Record

class S57RawDecoder(
    private val objectLookup: S57ObjectClassLookup = S57ObjectClassLookup,
    private val attributeLookup: S57AttributeLookup = S57AttributeLookup
) {
    fun decode(bytes: ByteArray): S57RawDataset = decode(Iso8211Reader().readRecords(bytes))

    fun decode(records: List<Iso8211Record>): S57RawDataset {
        val metadataParts = mutableListOf<S57DatasetMetadata>()
        val features = mutableListOf<S57RawFeatureRecord>()
        val vectors = mutableListOf<S57RawVectorRecord>()
        val unknown = mutableListOf<S57RawRecord>()

        records.forEachIndexed { index, record ->
            when {
                record.field("DSID") != null || record.field("DSSI") != null -> metadataParts += decodeMetadata(record)
                record.field("FRID") != null -> decodeFeature(record)?.let(features::add) ?: unknown.add(record.toUnknown(index))
                record.field("VRID") != null -> decodeVector(record)?.let(vectors::add) ?: unknown.add(record.toUnknown(index))
                else -> unknown += record.toUnknown(index)
            }
        }

        return S57RawDataset(
            metadata = mergeMetadata(metadataParts),
            features = features,
            vectors = vectors,
            unknownRecords = unknown
        )
    }

    private fun decodeFeature(record: Iso8211Record): S57RawFeatureRecord? {
        val frid = record.field("FRID")?.content ?: return null
        if (frid.textLooksLikeKeyValueFixture()) return decodeTextFeature(record)
        if (frid.size < 12) return decodeTextFeature(record)

        val rcnm = frid.u8(0)
        val rcid = frid.u32(1)
        val prim = S57Primitive.fromCode(frid.u8(5))
        val grup = frid.u8(6)
        val objl = frid.u16(7)
        val rver = frid.u16(9)
        val ruin = S57UpdateInstruction.fromCode(frid.u8(11))

        return S57RawFeatureRecord(
            id = rcid,
            recordName = S57RecordName(rcnm, rcid),
            primitive = prim,
            group = grup,
            objectClassCode = objl,
            objectClassAcronym = objectLookup.acronym(objl),
            version = rver,
            updateInstruction = ruin,
            featureObjectId = record.field("FOID")?.let(::decodeFoid),
            attributes = record.fields("ATTF").flatMap(::decodeAttributes),
            nationalAttributes = record.fields("NATF").flatMap(::decodeAttributes),
            spatialReferences = record.fields("FSPT").flatMap(::decodeSpatialReferences),
            rawFieldTags = record.fields.map { it.tag }.toSet()
        )
    }

    /** Accepts simple synthetic/text fixtures such as FRID="RCID=1;OBJL=42;PRIM=3". */
    private fun decodeTextFeature(record: Iso8211Record): S57RawFeatureRecord? {
        val pairs = keyValuePairs(record.field("FRID")?.text().orEmpty())
        if (pairs.isEmpty()) return null
        val rcid = pairs["RCID"]?.toLongOrNull() ?: pairs["ID"]?.toLongOrNull() ?: 0L
        val rcnm = pairs["RCNM"]?.toIntOrNull() ?: 100
        val objl = pairs["OBJL"]?.toIntOrNull() ?: 0
        val prim = S57Primitive.fromCode(pairs["PRIM"]?.toIntOrNull() ?: 0)
        return S57RawFeatureRecord(
            id = rcid,
            recordName = S57RecordName(rcnm, rcid),
            primitive = prim,
            group = pairs["GRUP"]?.toIntOrNull() ?: 0,
            objectClassCode = objl,
            objectClassAcronym = pairs["ACRONYM"] ?: objectLookup.acronym(objl),
            version = pairs["RVER"]?.toIntOrNull() ?: 1,
            updateInstruction = S57UpdateInstruction.fromCode(pairs["RUIN"]?.toIntOrNull() ?: 0),
            attributes = record.fields("ATTF").flatMap(::decodeAttributes),
            nationalAttributes = record.fields("NATF").flatMap(::decodeAttributes),
            spatialReferences = record.fields("FSPT").flatMap(::decodeSpatialReferences),
            rawFieldTags = record.fields.map { it.tag }.toSet()
        )
    }

    private fun decodeVector(record: Iso8211Record): S57RawVectorRecord? {
        val vrid = record.field("VRID")?.content ?: return null
        if (vrid.size < 8) return null
        val rcnm = vrid.u8(0)
        val rcid = vrid.u32(1)
        val rver = vrid.u16(5)
        val ruin = S57UpdateInstruction.fromCode(vrid.u8(7))
        val sg2d = record.fields("SG2D").flatMap(::decodeSg2dCoordinates)
        val sg3d = record.fields("SG3D").flatMap(::decodeSg3dCoordinates)
        val vrpt = record.fields("VRPT").flatMap(::decodeVectorReferences)
        return S57RawVectorRecord(
            id = rcid,
            recordName = S57RecordName(rcnm, rcid),
            version = rver,
            updateInstruction = ruin,
            twoDimensionalCoordinates = sg2d,
            threeDimensionalCoordinates = sg3d,
            vectorReferences = vrpt,
            rawFieldTags = record.fields.map { it.tag }.toSet()
        )
    }

    private fun decodeSg2dCoordinates(field: Iso8211Field): List<S57RawCoordinate> {
        val fromText = decodeTextCoordinates(field.text())
        if (fromText.isNotEmpty()) return fromText
        val data = field.content
        val result = mutableListOf<S57RawCoordinate>()
        var cursor = 0
        while (cursor + 8 <= data.size) {
            val y = data.i32(cursor)
            val x = data.i32(cursor + 4)
            result += S57RawCoordinate(yRaw = y, xRaw = x)
            cursor += 8
        }
        return result
    }

    private fun decodeSg3dCoordinates(field: Iso8211Field): List<S57RawCoordinate> {
        val fromText = decodeTextCoordinates(field.text())
        if (fromText.isNotEmpty()) return fromText
        val data = field.content
        val result = mutableListOf<S57RawCoordinate>()
        var cursor = 0
        while (cursor + 12 <= data.size) {
            val y = data.i32(cursor)
            val x = data.i32(cursor + 4)
            val z = data.i32(cursor + 8)
            result += S57RawCoordinate(yRaw = y, xRaw = x, zRaw = z)
            cursor += 12
        }
        return result
    }

    /**
     * Diagnostic/test convenience parser for textual coordinate fixtures.
     * Supported forms include:
     *   Y=407000000,X=-740000000;Y=407010000,X=-739990000
     *   -740000000,407000000;-739990000,407010000  (X,Y pairs)
     */
    private fun decodeTextCoordinates(text: String): List<S57RawCoordinate> {
        val cleaned = text.replace('\u001e', ';').replace('\u001f', ';').trim()
        if (cleaned.isBlank()) return emptyList()
        val groups = cleaned.split(';', '|', '\n').map { it.trim() }.filter { it.isNotBlank() }
        val result = mutableListOf<S57RawCoordinate>()
        for (group in groups) {
            val pairs = keyValuePairs(group)
            if (pairs.containsKey("X") || pairs.containsKey("Y") || pairs.containsKey("XCOO") || pairs.containsKey("YCOO")) {
                val x = (pairs["X"] ?: pairs["XCOO"] ?: pairs["LON"])?.toLongOrNull()
                val y = (pairs["Y"] ?: pairs["YCOO"] ?: pairs["LAT"])?.toLongOrNull()
                val z = (pairs["Z"] ?: pairs["VE3D"] ?: pairs["DEPTH"])?.toLongOrNull()
                if (x != null && y != null) result += S57RawCoordinate(yRaw = y, xRaw = x, zRaw = z)
            } else if (',' in group) {
                val nums = group.split(',').mapNotNull { it.trim().toLongOrNull() }
                if (nums.size >= 2) result += S57RawCoordinate(yRaw = nums[1], xRaw = nums[0], zRaw = nums.getOrNull(2))
            }
        }
        return result
    }

    private fun decodeFoid(field: Iso8211Field): S57FeatureObjectIdentifier? {
        val data = field.content
        if (data.size < 8) return null
        return S57FeatureObjectIdentifier(
            agency = data.u16(0),
            featureId = data.u32(2),
            subdivision = data.u16(6)
        )
    }

    private fun decodeAttributes(field: Iso8211Field): List<S57RawAttribute> {
        val data = field.content
        val text = field.text()
        if ('=' in text) {
            return keyValuePairs(text).map { (key, value) ->
                val code = key.removePrefix("ATTL_").toIntOrNull() ?: -1
                S57RawAttribute(code, if (code >= 0) attributeLookup.acronym(code) else key, value)
            }
        }

        val result = mutableListOf<S57RawAttribute>()
        var cursor = 0
        while (cursor + 2 <= data.size) {
            val code = data.u16(cursor)
            cursor += 2
            val end = data.indexOfTerminator(cursor)
            val value = data.copyOfRange(cursor, end).decodeToString().trimEnd('\u0000')
            result += S57RawAttribute(code, attributeLookup.acronym(code), value)
            cursor = if (end < data.size && data[end] == UNIT_TERMINATOR) end + 1 else end
            if (cursor >= data.size) break
        }
        return result
    }

    private fun decodeSpatialReferences(field: Iso8211Field): List<S57SpatialReference> {
        val data = field.content
        val result = mutableListOf<S57SpatialReference>()
        var cursor = 0
        while (cursor + 8 <= data.size) {
            val rcnm = data.u8(cursor)
            val rcid = data.u32(cursor + 1)
            val ornt = data.u8(cursor + 5)
            val usag = data.u8(cursor + 6)
            val mask = data.u8(cursor + 7)
            result += S57SpatialReference(S57RecordName(rcnm, rcid), ornt, usag, mask)
            cursor += 8
        }
        return result
    }

    private fun decodeVectorReferences(field: Iso8211Field): List<S57VectorReference> {
        val text = field.text()
        if ('=' in text) {
            return text.replace('\u001e', ';').replace('\u001f', ';')
                .split('|', '\n')
                .map { keyValuePairs(it) }
                .filter { it.isNotEmpty() }
                .map { pairs ->
                    val rcnm = pairs["RCNM"]?.toIntOrNull() ?: 0
                    val rcid = pairs["RCID"]?.toLongOrNull() ?: pairs["ID"]?.toLongOrNull() ?: 0L
                    S57VectorReference(
                        name = S57RecordName(rcnm, rcid),
                        orientation = pairs["ORNT"]?.toIntOrNull() ?: 1,
                        usage = pairs["USAG"]?.toIntOrNull() ?: 1,
                        topologyIndicator = pairs["TOPI"]?.toIntOrNull() ?: 0,
                        mask = pairs["MASK"]?.toIntOrNull() ?: 255
                    )
                }
        }
        val data = field.content
        val result = mutableListOf<S57VectorReference>()
        var cursor = 0
        while (cursor + 9 <= data.size) {
            val rcnm = data.u8(cursor)
            val rcid = data.u32(cursor + 1)
            val ornt = data.u8(cursor + 5)
            val usag = data.u8(cursor + 6)
            val topi = data.u8(cursor + 7)
            val mask = data.u8(cursor + 8)
            result += S57VectorReference(S57RecordName(rcnm, rcid), ornt, usag, topi, mask)
            cursor += 9
        }
        return result
    }

    private fun decodeMetadata(record: Iso8211Record): S57DatasetMetadata {
        val raw = record.fields.associate { it.tag to it.text().printableOnly() }
        val pairs = record.fields.flatMap { keyValuePairs(it.text()).entries }.associate { it.key to it.value }
        val dsidText = record.field("DSID")?.text().orEmpty().printableOnly()
        val binaryDsid = record.field("DSID")?.let { decodeBinaryDsid(it.content) }
        val binaryDspm = record.field("DSPM")?.let { decodeBinaryDspm(it.content) }
        return S57DatasetMetadata(
            cellName = pairs["DSNM"] ?: pairs["CELL"] ?: binaryDsid?.cellName ?: bestCellName(dsidText),
            edition = (pairs["EDTN"] ?: pairs["EDITION"])?.toIntOrNull() ?: binaryDsid?.edition,
            updateNumber = (pairs["UPDN"] ?: pairs["UPDATE"] ?: pairs["UPDNM"])?.toIntOrNull() ?: binaryDsid?.updateNumber,
            issueDate = pairs["ISDT"] ?: binaryDsid?.issueDate,
            updateApplicationDate = pairs["UADT"] ?: binaryDsid?.updateApplicationDate,
            productSpecification = pairs["STED"] ?: pairs["PROF"] ?: binaryDsid?.productSpecification,
            coordinateMultiplier = pairs["COMF"]?.toIntOrNull() ?: binaryDspm?.coordinateMultiplier,
            soundingMultiplier = pairs["SOMF"]?.toIntOrNull() ?: binaryDspm?.soundingMultiplier,
            rawFields = raw
        )
    }

    private fun decodeBinaryDsid(data: ByteArray): BinaryDsidMetadata {
        // S-57 DSID is mostly binary: RCNM(1), RCID(4), EXPP(1), INTU(1),
        // followed by variable text subfields DSNM, EDTN, UPDN, UADT, ISDT, STED, ...
        // Treating the whole field as printable text can pick the binary RCID bytes
        // as a bogus cell name such as "1600". Decode the fixed prefix first.
        if (data.size < DSID_TEXT_OFFSET + 1) return BinaryDsidMetadata()
        val fields = data.terminatedTextFields(DSID_TEXT_OFFSET)
        val cellName = fields.getOrNull(0).orEmpty().trim().takeIf { looksLikeS57CellName(it) }
        return BinaryDsidMetadata(
            cellName = cellName,
            edition = fields.getOrNull(1)?.trim()?.toIntOrNull(),
            updateNumber = fields.getOrNull(2)?.trim()?.toIntOrNull(),
            updateApplicationDate = fields.getOrNull(3)?.trim()?.takeIf { it.isNotBlank() },
            issueDate = fields.getOrNull(4)?.trim()?.takeIf { it.isNotBlank() },
            productSpecification = fields.getOrNull(5)?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    private fun decodeBinaryDspm(data: ByteArray): BinaryDspmMetadata {
        // S-57 DSPM fixed prefix: RCNM(1), RCID(4), HDAT/VDAT/SDAT(1 each),
        // CSCL(4), DUNI/HUNI/PUNI/COUN(1 each), COMF(4), SOMF(4), then COMT.
        if (data.size < DSPM_SOMF_OFFSET + 4) return BinaryDspmMetadata()
        return BinaryDspmMetadata(
            coordinateMultiplier = data.u32(DSPM_COMF_OFFSET).positiveIntOrNull(),
            soundingMultiplier = data.u32(DSPM_SOMF_OFFSET).positiveIntOrNull()
        )
    }

    private data class BinaryDsidMetadata(
        val cellName: String? = null,
        val edition: Int? = null,
        val updateNumber: Int? = null,
        val issueDate: String? = null,
        val updateApplicationDate: String? = null,
        val productSpecification: String? = null
    )

    private data class BinaryDspmMetadata(
        val coordinateMultiplier: Int? = null,
        val soundingMultiplier: Int? = null
    )

    private fun ByteArray.terminatedTextFields(startOffset: Int): List<String> {
        if (startOffset >= size) return emptyList()
        val result = mutableListOf<String>()
        var start = startOffset
        var index = startOffset
        while (index < size) {
            val value = this[index]
            if (value == UNIT_TERMINATOR || value == FIELD_TERMINATOR) {
                result += copyOfRange(start, index).decodeToString().trimEnd('\u0000')
                start = index + 1
                if (value == FIELD_TERMINATOR) return result
            }
            index++
        }
        if (start <= size) result += copyOfRange(start, size).decodeToString().trimEnd('\u0000')
        return result
    }

    private fun looksLikeS57CellName(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.length in 4..16 && trimmed.any { it.isLetter() } && trimmed.all { it.isLetterOrDigit() || it == '_' || it == '-' }
    }

    private fun Long.positiveIntOrNull(): Int? = takeIf { it > 0L && it <= Int.MAX_VALUE }?.toInt()

    private fun mergeMetadata(parts: List<S57DatasetMetadata>): S57DatasetMetadata {
        if (parts.isEmpty()) return S57DatasetMetadata()
        return S57DatasetMetadata(
            cellName = parts.firstNotBlank { it.cellName },
            edition = parts.firstNotNullOfOrNull { it.edition },
            updateNumber = parts.firstNotNullOfOrNull { it.updateNumber },
            issueDate = parts.firstNotNullOfOrNull { it.issueDate },
            updateApplicationDate = parts.firstNotNullOfOrNull { it.updateApplicationDate },
            productSpecification = parts.firstNotNullOfOrNull { it.productSpecification },
            coordinateMultiplier = parts.firstNotNullOfOrNull { it.coordinateMultiplier },
            soundingMultiplier = parts.firstNotNullOfOrNull { it.soundingMultiplier },
            rawFields = parts.flatMap { it.rawFields.entries }.associate { it.key to it.value }
        )
    }

    private fun Iso8211Record.toUnknown(index: Int): S57RawRecord = S57RawRecord(
        index = index,
        tags = fields.map { it.tag },
        preview = fields.joinToString(" ") { it.tag + "=" + it.text().printableOnly().take(24) }
    )

    private fun keyValuePairs(text: String): Map<String, String> = text
        .replace('\u001e', ';')
        .replace('\u001f', ';')
        .split(';', '|', '\n')
        .mapNotNull { token ->
            val idx = token.indexOf('=')
            if (idx <= 0) null else token.substring(0, idx).trim().uppercase() to token.substring(idx + 1).trim()
        }
        .toMap()

    private fun ByteArray.textLooksLikeKeyValueFixture(): Boolean {
        val text = decodeToString().trimEnd('\u0000', '\u001e', '\u001f')
        return '=' in text && keyValuePairs(text).isNotEmpty()
    }

    private fun bestCellName(text: String): String {
        val token = text.split(';', '|', ' ', '\u0000').firstOrNull { it.length in 3..32 && it.any(Char::isLetterOrDigit) }
        return token.orEmpty()
    }

    private fun String.printableOnly(): String = map { ch -> if (ch.code in 32..126) ch else ';' }.joinToString("").trim(';', ' ')

    private fun List<S57DatasetMetadata>.firstNotBlank(selector: (S57DatasetMetadata) -> String): String =
        firstNotNullOfOrNull { selector(it).takeIf(String::isNotBlank) }.orEmpty()

    private fun ByteArray.u8(offset: Int): Int = if (offset in indices) this[offset].toInt() and 0xff else 0
    private fun ByteArray.u16(offset: Int): Int = u8(offset) or (u8(offset + 1) shl 8)
    private fun ByteArray.u32(offset: Int): Long =
        u8(offset).toLong() or (u8(offset + 1).toLong() shl 8) or (u8(offset + 2).toLong() shl 16) or (u8(offset + 3).toLong() shl 24)

    private fun ByteArray.i32(offset: Int): Long =
        (u32(offset).toInt()).toLong()

    private fun ByteArray.indexOfTerminator(start: Int): Int {
        var i = start
        while (i < size && this[i] != UNIT_TERMINATOR && this[i] != FIELD_TERMINATOR) i++
        return i
    }

    private companion object {
        const val DSID_TEXT_OFFSET: Int = 7
        const val DSPM_COMF_OFFSET: Int = 16
        const val DSPM_SOMF_OFFSET: Int = 20
        val UNIT_TERMINATOR: Byte = Iso8211Reader.UNIT_TERMINATOR_BYTE
        val FIELD_TERMINATOR: Byte = Iso8211Reader.FIELD_TERMINATOR_BYTE
    }
}
