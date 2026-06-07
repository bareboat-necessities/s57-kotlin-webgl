package io.github.s57.iso8211

/** Human-readable ISO8211 dump used by tests, CLI tooling, and browser diagnostics. */
object Iso8211RecordDumper {
    fun summarize(records: List<Iso8211Record>, maxFieldsPerRecord: Int = 24): String = buildString {
        appendLine("ISO8211 records=${records.size}")
        records.forEachIndexed { index, record ->
            appendLine(
                "record[$index] offset=${record.rawOffset} length=${record.rawLength} " +
                    "leader=${record.leader.leaderIdentifier} fields=${record.fields.size} base=${record.leader.baseAddressOfFieldArea}"
            )
            record.fields.take(maxFieldsPerRecord).forEach { field ->
                appendLine(
                    "  ${field.tag} length=${field.data.size} position=${field.directoryEntry.fieldPosition} " +
                        "subfields=${field.subfields.size} text=${field.text().printablePreview()}"
                )
            }
            if (record.fields.size > maxFieldsPerRecord) appendLine("  ... +${record.fields.size - maxFieldsPerRecord} fields")
        }
    }

    private fun String.printablePreview(maxLength: Int = 48): String {
        val printable = map { if (it.code in 32..126) it else '·' }.joinToString("")
        return if (printable.length <= maxLength) printable else printable.take(maxLength) + "…"
    }
}
