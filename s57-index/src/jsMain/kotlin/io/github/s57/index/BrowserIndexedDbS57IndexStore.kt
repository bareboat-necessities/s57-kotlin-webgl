package io.github.s57.index

/** Browser IndexedDB schema names used by the Phase 5 chart cache. */
object BrowserS57IndexedDbSchema {
    const val DatabaseName: String = "s57-kotlin-webgl"
    const val Version: Int = 1
    const val Cells: String = "cells"
    const val Features: String = "features"
    const val Geometries: String = "geometries"
    const val SpatialBins: String = "spatialBins"
}

sealed class BrowserIndexedDbOpenResult {
    data class Success(val databaseName: String, val version: Int) : BrowserIndexedDbOpenResult()
    data class Unavailable(val reason: String) : BrowserIndexedDbOpenResult()
    data class Failure(val message: String) : BrowserIndexedDbOpenResult()
}

/**
 * Minimal JS IndexedDB opener/schema initializer.
 *
 * Phase 5 keeps the portable query/index algorithm in commonMain and introduces
 * this browser DB boundary without depending on a third-party IndexedDB wrapper.
 * Later phases can fill in async object-store put/get operations behind the
 * same schema.
 */
class BrowserIndexedDbS57IndexStore(
    private val databaseName: String = BrowserS57IndexedDbSchema.DatabaseName,
    private val version: Int = BrowserS57IndexedDbSchema.Version
) {
    fun isAvailable(): Boolean = js("typeof indexedDB !== 'undefined'") as Boolean

    fun open(callback: (BrowserIndexedDbOpenResult) -> Unit) {
        if (!isAvailable()) {
            callback(BrowserIndexedDbOpenResult.Unavailable("IndexedDB is not available in this browser context"))
            return
        }
        val indexedDb = js("indexedDB")
        val request = indexedDb.open(databaseName, version)
        request.onupgradeneeded = { _: dynamic ->
            val db = request.result
            ensureStore(db, BrowserS57IndexedDbSchema.Cells, js("({ keyPath: 'cellId' })"))
            ensureStore(db, BrowserS57IndexedDbSchema.Features, js("({ keyPath: 'storageKey' })"))
            ensureStore(db, BrowserS57IndexedDbSchema.Geometries, js("({ keyPath: 'geometryId' })"))
            ensureStore(db, BrowserS57IndexedDbSchema.SpatialBins, js("({ keyPath: 'binId' })"))
            null
        }
        request.onsuccess = { _: dynamic ->
            val db = request.result
            db.close()
            callback(BrowserIndexedDbOpenResult.Success(databaseName, version))
            null
        }
        request.onerror = { _: dynamic ->
            val error = request.error
            callback(BrowserIndexedDbOpenResult.Failure(error?.message as? String ?: "IndexedDB open failed"))
            null
        }
    }

    private fun ensureStore(db: dynamic, name: String, options: dynamic) {
        val names = db.objectStoreNames
        if (!(names.contains(name) as Boolean)) db.createObjectStore(name, options)
    }
}
