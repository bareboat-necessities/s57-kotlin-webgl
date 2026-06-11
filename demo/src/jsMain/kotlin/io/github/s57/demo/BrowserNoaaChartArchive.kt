package io.github.s57.demo

import io.github.s57.render.toS57ByteArray
import kotlin.js.Promise
import org.khronos.webgl.ArrayBuffer
import org.w3c.files.File

@JsModule("jszip")
@JsNonModule
private external class JsZip {
    fun loadAsync(data: ArrayBuffer): Promise<JsZip>
    val files: dynamic
}

private external fun decodeURIComponent(encodedURI: String): String

private fun objectKeys(obj: dynamic): Array<String> = js("Object.keys(obj)").unsafeCast<Array<String>>()

data class BrowserNoaaChartPayload(
    val path: String,
    val chartId: String,
    val updateNumber: Int,
    val bytes: ByteArray
) {
    val fileName: String get() = path.substringAfterLast('/')
    val label: String get() = "$path — ${bytes.size} bytes"
}

data class BrowserNoaaChartGroup(
    val chartId: String,
    val payloads: List<BrowserNoaaChartPayload>
) {
    val base: BrowserNoaaChartPayload? get() = payloads.firstOrNull { it.updateNumber == 0 }
    val importPayloads: List<BrowserNoaaChartPayload> get() = payloads.sortedBy { it.updateNumber }
    val contiguousImportPayloads: List<BrowserNoaaChartPayload>
        get() {
            val ordered = importPayloads
            val out = mutableListOf<BrowserNoaaChartPayload>()
            var expected = 0
            for (payload in ordered) {
                if (payload.updateNumber != expected) break
                out += payload
                expected += 1
            }
            return out
        }
    val firstMissingUpdateNumber: Int?
        get() {
            val ordered = importPayloads
            var expected = 0
            for (payload in ordered) {
                if (payload.updateNumber != expected) return expected
                expected += 1
            }
            return null
        }
    val label: String get() = buildString {
        append(chartId)
        append(" (base .000")
        val updates = importPayloads.filter { it.updateNumber > 0 }
        if (updates.isNotEmpty()) append(" + updates ." + updates.joinToString(" .") { it.updateNumber.toString().padStart(3, '0') })
        append(")")
    }
}

fun groupNoaaChartPayloads(payloads: List<BrowserNoaaChartPayload>): List<BrowserNoaaChartGroup> = payloads
    .groupBy { it.chartId.uppercase() }
    .map { (chartId, entries) -> BrowserNoaaChartGroup(chartId, entries.distinctBy { it.updateNumber }.sortedBy { it.updateNumber }) }
    .sortedBy { it.chartId }

fun noaaChartPathOrNull(path: String): Pair<String, Int>? {
    val normalized = path.replace('\\', '/').substringAfterLast('/')
    val dot = normalized.lastIndexOf('.')
    if (dot <= 0 || dot == normalized.lastIndex) return null
    val chartId = normalized.substring(0, dot).trim().uppercase()
    val extension = normalized.substring(dot + 1)
    if (extension.length != 3 || extension.any { it !in '0'..'9' }) return null
    return chartId to extension.toInt()
}

fun extractNoaaChartsFromZip(file: File, buffer: ArrayBuffer, callback: (Result<List<BrowserNoaaChartPayload>>) -> Unit) {
    JsZip().loadAsync(buffer).then(
        { zip ->
            val matches = objectKeys(zip.files)
                .mapNotNull { key ->
                    val entry = zip.files[key]
                    val isDirectory = entry.dir == true
                    val decodedPath = try { decodeURIComponent(key) } catch (_: Throwable) { key }
                    val parsed = noaaChartPathOrNull(decodedPath)
                    if (!isDirectory && parsed != null) Triple(entry, decodedPath, parsed) else null
                }
            if (matches.isEmpty()) {
                callback(Result.failure(IllegalArgumentException("${file.name} did not contain NOAA ENC .000/.001/... chart files")))
            } else {
                val payloads = mutableListOf<BrowserNoaaChartPayload>()
                fun next(index: Int) {
                    if (index >= matches.size) {
                        callback(Result.success(payloads.sortedWith(compareBy<BrowserNoaaChartPayload> { it.chartId }.thenBy { it.updateNumber }.thenBy { it.path })))
                        return
                    }
                    val (entry, path, parsed) = matches[index]
                    entry.async("arraybuffer").then(
                        { raw: dynamic ->
                            val buffer = raw.unsafeCast<ArrayBuffer>()
                            payloads += BrowserNoaaChartPayload(
                                path = path,
                                chartId = parsed.first,
                                updateNumber = parsed.second,
                                bytes = buffer.toS57ByteArray()
                            )
                            next(index + 1)
                            null
                        },
                        { error: dynamic ->
                            callback(Result.failure(IllegalStateException("Failed to extract $path from ${file.name}: ${error?.message ?: error}")))
                            null
                        }
                    )
                }
                next(0)
            }
            null
        },
        { error: dynamic ->
            callback(Result.failure(IllegalArgumentException("Failed to open ZIP ${file.name}: ${error?.message ?: error}")))
            null
        }
    )
}
