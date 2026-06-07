package io.github.s57.iso8211

import java.io.File

/** JVM diagnostic entry point: prints ISO8211 record/field structure without S-57 decoding. */
fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Usage: Iso8211DumpMain <path-to-enc-or-iso8211-file>" }
    val file = File(args[0])
    require(file.isFile) { "Input file does not exist: ${file.absolutePath}" }
    val records = Iso8211Reader().readRecords(file.readBytes())
    print(Iso8211RecordDumper.summarize(records))
}
