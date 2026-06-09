package io.github.s57.render

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.files.File
import org.w3c.files.FileReader

/** Browser helper that reads a user-selected File and imports it through S57WebGlEngine. */
class BrowserS57FileImporter(
    private val engine: S57WebGlEngine
) {
    fun importFile(file: File, callback: (Result<S57EngineImportResult>) -> Unit) {
        readFileBytes(file) { result ->
            result.fold(
                onSuccess = { bytes ->
                    try {
                        callback(Result.success(engine.importS57Bytes(bytes)))
                    } catch (t: Throwable) {
                        callback(Result.failure(t))
                    }
                },
                onFailure = { callback(Result.failure(it)) }
            )
        }
    }

    fun readFileBytes(file: File, callback: (Result<ByteArray>) -> Unit) {
        val reader = FileReader()
        reader.onload = {
            try {
                val buffer = reader.result as ArrayBuffer
                callback(Result.success(buffer.toByteArray()))
            } catch (t: Throwable) {
                callback(Result.failure(t))
            }
            null
        }
        reader.onerror = {
            callback(Result.failure(IllegalStateException("Failed to read ${file.name}")))
            null
        }
        reader.readAsArrayBuffer(file)
    }
}

internal fun ArrayBuffer.toS57ByteArray(): ByteArray = toByteArray()

internal fun ByteArray.toInt8Array(): Int8Array {
    val view = Int8Array(size)
    for (index in indices) view[index] = this[index]
    return view
}

internal fun Int8Array.toByteArray(): ByteArray = ByteArray(length) { index -> this[index] }

private fun ArrayBuffer.toByteArray(): ByteArray {
    val view = Int8Array(this)
    return view.toByteArray()
}
