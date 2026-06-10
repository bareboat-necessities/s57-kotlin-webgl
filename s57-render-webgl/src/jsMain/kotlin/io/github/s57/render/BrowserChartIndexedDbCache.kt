package io.github.s57.render

import kotlinx.browser.window
import org.khronos.webgl.Int8Array

/** Phase 23 IndexedDB persistence for browser-selected ENC payloads. */
class BrowserChartIndexedDbCache(
    private val databaseName: String = "s57-kotlin-webgl-cache",
    private val storeName: String = "encPayloads",
    private val version: Int = 1
) {
    fun list(callback: (Result<List<BrowserChartCacheEntry>>) -> Unit) {
        open { opened ->
            opened.fold(
                onSuccess = { db ->
                    try {
                        val tx = db.asDynamic().transaction(storeName, "readonly")
                        val store = tx.objectStore(storeName)
                        val request = store.getAll()
                        request.onsuccess = {
                            val rows = request.result
                            val entries = mutableListOf<BrowserChartCacheEntry>()
                            val length = (rows.length as Number).toInt()
                            for (index in 0 until length) {
                                val entry = rows.asDynamic()[index].toCacheEntryOrNull()
                                if (entry != null) entries.add(entry)
                            }
                            callback(Result.success(entries))
                            null
                        }
                        request.onerror = {
                            callback(Result.failure(IllegalStateException("IndexedDB list failed")))
                            null
                        }
                    } catch (t: Throwable) {
                        callback(Result.failure(t))
                    }
                },
                onFailure = { callback(Result.failure(it)) }
            )
        }
    }

    fun put(fileName: String, bytes: ByteArray, importResult: S57EngineImportResult, callback: (Result<BrowserChartCacheEntry>) -> Unit) {
        open { opened ->
            opened.fold(
                onSuccess = { db ->
                    try {
                        val key = browserChartCacheKey(fileName, bytes.size)
                        val entry = BrowserChartCacheEntry(
                            cacheKey = key,
                            fileName = fileName,
                            byteCount = bytes.size,
                            cellId = importResult.cell.cellId,
                            featureCount = importResult.cell.featureCount,
                            cachedAtMillis = window.performance.now()
                        )
                        val row = js("({})")
                        row.cacheKey = entry.cacheKey
                        row.fileName = entry.fileName
                        row.byteCount = entry.byteCount
                        row.cellId = entry.cellId
                        row.featureCount = entry.featureCount
                        row.cachedAtMillis = entry.cachedAtMillis
                        row.payload = bytes.toInt8Array()

                        val tx = db.asDynamic().transaction(storeName, "readwrite")
                        val store = tx.objectStore(storeName)
                        val request = store.put(row)
                        request.onsuccess = {
                            callback(Result.success(entry))
                            null
                        }
                        request.onerror = {
                            callback(Result.failure(IllegalStateException("IndexedDB put failed for $fileName")))
                            null
                        }
                    } catch (t: Throwable) {
                        callback(Result.failure(t))
                    }
                },
                onFailure = { callback(Result.failure(it)) }
            )
        }
    }

    fun load(cacheKey: String, callback: (Result<CachedChartPayload?>) -> Unit) {
        open { opened ->
            opened.fold(
                onSuccess = { db ->
                    try {
                        val tx = db.asDynamic().transaction(storeName, "readonly")
                        val store = tx.objectStore(storeName)
                        val request = store.get(cacheKey)
                        request.onsuccess = {
                            val row = request.result
                            if (row == null) {
                                callback(Result.success(null))
                            } else {
                                val entry = row.toCacheEntryOrNull()
                                val payload = row.asDynamic().payload.unsafeCast<Int8Array>().toByteArray()
                                callback(Result.success(if (entry == null) null else CachedChartPayload(entry, payload)))
                            }
                            null
                        }
                        request.onerror = {
                            callback(Result.failure(IllegalStateException("IndexedDB load failed for $cacheKey")))
                            null
                        }
                    } catch (t: Throwable) {
                        callback(Result.failure(t))
                    }
                },
                onFailure = { callback(Result.failure(it)) }
            )
        }
    }

    fun clear(callback: (Result<Unit>) -> Unit) {
        open { opened ->
            opened.fold(
                onSuccess = { db ->
                    try {
                        val tx = db.asDynamic().transaction(storeName, "readwrite")
                        val store = tx.objectStore(storeName)
                        val request = store.clear()
                        request.onsuccess = {
                            callback(Result.success(Unit))
                            null
                        }
                        request.onerror = {
                            callback(Result.failure(IllegalStateException("IndexedDB clear failed")))
                            null
                        }
                    } catch (t: Throwable) {
                        callback(Result.failure(t))
                    }
                },
                onFailure = { callback(Result.failure(it)) }
            )
        }
    }

    private fun open(callback: (Result<Any>) -> Unit) {
        val indexedDb = window.asDynamic().indexedDB
        if (indexedDb == null) {
            callback(Result.failure(IllegalStateException("IndexedDB is not available in this browser")))
            return
        }
        val request = indexedDb.open(databaseName, version)
        request.onupgradeneeded = {
            val db = request.result
            if (!db.objectStoreNames.contains(storeName)) {
                db.createObjectStore(storeName, js("({ keyPath: 'cacheKey' })"))
            }
            null
        }
        request.onsuccess = {
            callback(Result.success(request.result as Any))
            null
        }
        request.onerror = {
            callback(Result.failure(IllegalStateException("IndexedDB open failed")))
            null
        }
    }
}

data class CachedChartPayload(
    val entry: BrowserChartCacheEntry,
    val bytes: ByteArray
)

private fun Any?.toCacheEntryOrNull(): BrowserChartCacheEntry? = try {
    val row = this.asDynamic()
    BrowserChartCacheEntry(
        cacheKey = row.cacheKey as String,
        fileName = row.fileName as String,
        byteCount = (row.byteCount as Number).toInt(),
        cellId = row.cellId as String,
        featureCount = (row.featureCount as Number).toInt(),
        cachedAtMillis = (row.cachedAtMillis as Number).toDouble()
    )
} catch (_: Throwable) {
    null
}
