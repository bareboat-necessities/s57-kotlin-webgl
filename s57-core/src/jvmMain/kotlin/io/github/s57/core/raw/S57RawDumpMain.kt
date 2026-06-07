package io.github.s57.core.raw

import java.io.File

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Usage: S57RawDumpMain <s57-enc-file.000>" }
    val file = File(args[0])
    require(file.isFile) { "S-57 file does not exist: ${file.absolutePath}" }
    val dataset = S57RawDecoder().decode(file.readBytes())
    println(S57RawDumper.summarize(dataset))
}
