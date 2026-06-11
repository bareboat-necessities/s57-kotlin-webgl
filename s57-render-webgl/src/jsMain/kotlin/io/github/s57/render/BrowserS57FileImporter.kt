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
                onSuccess = { bytes -> callback(importBytes(bytes)) },
                onFailure = { callback(Result.failure(it)) }
            )
        }
    }

    fun importArrayBuffer(buffer: ArrayBuffer): Result<S57EngineImportResult> = importBytes(buffer.toByteArray())

    fun readFileBytes(file: File, callback: (Result<ByteArray>) -> Unit) {
        readFileArrayBuffer(file) { result ->
            result.fold(
                onSuccess = { callback(Result.success(it.toByteArray())) },
                onFailure = { callback(Result.failure(it)) }
            )
        }
    }

    fun readFileArrayBuffer(file: File, callback: (Result<ArrayBuffer>) -> Unit) {
        val reader = FileReader()
        reader.onload = {
            try {
                callback(Result.success(reader.result as ArrayBuffer))
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

    private fun importBytes(bytes: ByteArray): Result<S57EngineImportResult> = try {
        Result.success(engine.importS57Bytes(bytes))
    } catch (t: Throwable) {
        Result.failure(t)
    }
}

fun ArrayBuffer.toS57ByteArray(): ByteArray = toByteArray()

internal fun ByteArray.toInt8Array(): Int8Array {
    val view = Int8Array(size)
    val dynamicView = view.asDynamic()
    for (index in indices) dynamicView[index] = this[index].toInt()
    return view
}

internal fun Int8Array.toByteArray(): ByteArray {
    val bytes = ByteArray(length)
    val dynamicView = asDynamic()
    for (index in 0 until length) {
        val value: Int = dynamicView[index]
        bytes[index] = value.toByte()
    }
    return bytes
}

private fun ArrayBuffer.toByteArray(): ByteArray = Int8Array(this).toByteArray()
