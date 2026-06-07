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
        val reader = FileReader()
        reader.onload = {
            try {
                val buffer = reader.result as ArrayBuffer
                callback(Result.success(engine.importS57Bytes(buffer.toByteArray())))
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

    private fun ArrayBuffer.toByteArray(): ByteArray {
        val view = Int8Array(this)
        return ByteArray(view.length) { index -> view[index] }
    }
}
