package io.github.s57.render

import io.github.s57.core.GeoBounds
import io.github.s57.core.GeoPoint
import io.github.s57.core.S57CellSummary
import io.github.s57.core.S57Dataset
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import io.github.s57.core.S57Value
import kotlinx.browser.window
import org.khronos.webgl.Int8Array

/** IndexedDB persistence for decoded browser ENC chart objects. */
class BrowserChartIndexedDbCache(
    private val databaseName: String = "s57-kotlin-webgl-cache",
    private val storeName: String = "encPayloads",
    private val version: Int = 2
) {
    private var cachedDb: dynamic = null

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
                                val entry = toCacheEntryOrNull(rows.asDynamic()[index])
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
        putSequence(fileName, listOf(bytes), importResult, callback)
    }

    fun putSequence(fileName: String, payloads: List<ByteArray>, importResult: S57EngineImportResult, callback: (Result<BrowserChartCacheEntry>) -> Unit) {
        open { opened ->
            opened.fold(
                onSuccess = { db ->
                    try {
                        val byteCount = payloads.sumOf { it.size }
                        val key = browserChartCacheKey(fileName, byteCount)
                        val entry = BrowserChartCacheEntry(
                            cacheKey = key,
                            fileName = fileName,
                            byteCount = byteCount,
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
                        // Persist decoded chart objects for fast restore, and keep the original payloads
                        // as a fallback for older/newer schema gaps or structured-clone incompatibilities.
                        row.dataset = importResult.sourceImport?.dataset?.toIndexedDbRowDataset()
                        row.payloads = payloads.map { it.toInt8Array() }.toTypedArray()
                        row.payload = payloads.firstOrNull()?.toInt8Array()

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
                                val entry = toCacheEntryOrNull(row)
                                val payloads = toPayloadList(row)
                                callback(Result.success(if (entry == null || payloads.isEmpty()) null else CachedChartPayload(entry, payloads)))
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

    fun loadDataset(cacheKey: String, callback: (Result<S57Dataset?>) -> Unit) {
        open { opened ->
            opened.fold(
                onSuccess = { db ->
                    try {
                        val tx = db.asDynamic().transaction(storeName, "readonly")
                        val store = tx.objectStore(storeName)
                        val request = store.get(cacheKey)
                        request.onsuccess = {
                            val row = request.result
                            val datasetRow: Any? = if (row == null) null else row.asDynamic().dataset
                            callback(Result.success(toS57DatasetOrNull(datasetRow)))
                            null
                        }
                        request.onerror = {
                            callback(Result.failure(IllegalStateException("IndexedDB dataset load failed for $cacheKey")))
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
        val alreadyOpen = cachedDb
        if (alreadyOpen != null) {
            callback(Result.success(alreadyOpen as Any))
            return
        }
        val indexedDb = window.asDynamic().indexedDB
        if (indexedDb == null) {
            callback(Result.failure(IllegalStateException("IndexedDB is not available in this browser")))
            return
        }
        val request = indexedDb.open(databaseName, version)
        request.onupgradeneeded = {
            val db = request.result
            val store = if (!db.objectStoreNames.contains(storeName)) {
                db.createObjectStore(storeName, js("({ keyPath: 'cacheKey' })"))
            } else {
                request.transaction.objectStore(storeName)
            }
            ensureIndex(store, "cellId", "cellId")
            ensureIndex(store, "fileName", "fileName")
            null
        }
        request.onsuccess = {
            cachedDb = request.result
            cachedDb.onclose = { cachedDb = null; null }
            cachedDb.onversionchange = {
                try { cachedDb.close() } catch (_: Throwable) { }
                cachedDb = null
                null
            }
            callback(Result.success(cachedDb as Any))
            null
        }
        request.onerror = {
            cachedDb = null
            callback(Result.failure(IllegalStateException("IndexedDB open failed")))
            null
        }
        request.onblocked = {
            cachedDb = null
            callback(Result.failure(IllegalStateException("IndexedDB open blocked by another tab; close old demo tabs and retry")))
            null
        }
    }

    private fun ensureIndex(store: dynamic, name: String, keyPath: String) {
        val names = store.indexNames
        if (!(names.contains(name) as Boolean)) store.createIndex(name, keyPath, js("({ unique: false })"))
    }
}

data class CachedChartPayload(
    val entry: BrowserChartCacheEntry,
    val payloads: List<ByteArray>
) {
    val bytes: ByteArray get() = payloads.first()
}

private fun toCacheEntryOrNull(value: Any?): BrowserChartCacheEntry? = try {
    if (value == null) {
        null
    } else {
        val row = value.asDynamic()
        BrowserChartCacheEntry(
            cacheKey = row.cacheKey as String,
            fileName = row.fileName as String,
            byteCount = (row.byteCount as Number).toInt(),
            cellId = row.cellId as String,
            featureCount = (row.featureCount as Number).toInt(),
            cachedAtMillis = (row.cachedAtMillis as Number).toDouble()
        )
    }
} catch (_: Throwable) {
    null
}

private fun toPayloadList(value: Any?): List<ByteArray> = try {
    if (value == null) {
        emptyList()
    } else {
        val row = value.asDynamic()
        val payloads = row.payloads
        if (payloads != null) {
            val length = (payloads.length as Number).toInt()
            (0 until length).mapNotNull { index -> payloads[index]?.unsafeCast<Int8Array>()?.toByteArray() }
        } else {
            listOf(row.payload.unsafeCast<Int8Array>().toByteArray())
        }
    }
} catch (_: Throwable) {
    emptyList()
}

private fun S57Dataset.toIndexedDbRowDataset(): dynamic {
    val row = js("({})")
    row.summary = summary.toIndexedDbRowSummary()
    row.features = features.map { it.toIndexedDbRowFeature() }.toTypedArray()
    return row
}

private fun S57CellSummary.toIndexedDbRowSummary(): dynamic {
    val row = js("({})")
    row.cellId = cellId
    row.name = name
    row.edition = edition
    row.updateNumber = updateNumber
    row.bounds = bounds?.toIndexedDbRowBounds()
    row.featureCount = featureCount
    return row
}

private fun GeoBounds.toIndexedDbRowBounds(): dynamic {
    val row = js("({})")
    row.minLon = minLon
    row.minLat = minLat
    row.maxLon = maxLon
    row.maxLat = maxLat
    return row
}

private fun S57Feature.toIndexedDbRowFeature(): dynamic {
    val row = js("({})")
    row.id = id.toString()
    row.objectClass = objectClass
    row.attributes = attributes.entries.map { entry ->
        val attr = js("({})")
        attr.key = entry.key
        attr.value = entry.value.toIndexedDbRowValue()
        attr
    }.toTypedArray()
    row.geometry = geometry.toIndexedDbRowGeometry()
    row.bounds = bounds?.toIndexedDbRowBounds()
    return row
}

private fun S57Value.toIndexedDbRowValue(): dynamic {
    val row = js("({})")
    when (this) {
        is S57Value.Text -> {
            row.type = "text"
            row.value = value
        }
        is S57Value.Integer -> {
            row.type = "integer"
            row.value = value
        }
        is S57Value.Decimal -> {
            row.type = "decimal"
            row.value = value
        }
        is S57Value.ListValue -> {
            row.type = "list"
            row.values = values.map { it.toIndexedDbRowValue() }.toTypedArray()
        }
        S57Value.Empty -> row.type = "empty"
    }
    return row
}

private fun S57Geometry.toIndexedDbRowGeometry(): dynamic {
    val row = js("({})")
    when (this) {
        S57Geometry.Empty -> row.type = "empty"
        is S57Geometry.Point -> {
            row.type = "point"
            row.coordinate = coordinate.toIndexedDbRowPoint()
        }
        is S57Geometry.MultiPoint -> {
            row.type = "multipoint"
            row.points = points.map { it.toIndexedDbRowPoint() }.toTypedArray()
        }
        is S57Geometry.LineString -> {
            row.type = "linestring"
            row.points = points.map { it.toIndexedDbRowPoint() }.toTypedArray()
        }
        is S57Geometry.Polygon -> {
            row.type = "polygon"
            row.rings = rings.map { ring -> ring.map { it.toIndexedDbRowPoint() }.toTypedArray() }.toTypedArray()
        }
        is S57Geometry.MultiPolygon -> {
            row.type = "multipolygon"
            row.polygons = polygons.map { polygon -> polygon.toIndexedDbRowGeometry() }.toTypedArray()
        }
    }
    return row
}

private fun GeoPoint.toIndexedDbRowPoint(): dynamic {
    val row = js("({})")
    row.lon = lon
    row.lat = lat
    return row
}

private fun toS57DatasetOrNull(value: Any?): S57Dataset? = try {
    if (value == null) {
        null
    } else {
        val row = value.asDynamic()
        val summary = toS57CellSummaryOrNull(row.summary)
        if (summary == null) {
            null
        } else {
            val featuresRow = row.features
            if (featuresRow == null) {
                null
            } else {
                val length = (featuresRow.length as Number).toInt()
                val features = (0 until length).mapNotNull { index -> toS57FeatureOrNull(featuresRow[index]) }
                val featureCount = summary.featureCount.takeIf { count -> count > 0 } ?: features.size
                S57Dataset(summary = summary.copy(featureCount = featureCount), features = features)
            }
        }
    }
} catch (_: Throwable) {
    null
}

private fun toS57CellSummaryOrNull(value: Any?): S57CellSummary? = try {
    if (value == null) {
        null
    } else {
        val row = value.asDynamic()
        val cellId = row.cellId as? String
        if (cellId == null) {
            null
        } else {
            S57CellSummary(
                cellId = cellId,
                name = row.name as? String ?: cellId,
                edition = (row.edition as? Number)?.toInt(),
                updateNumber = (row.updateNumber as? Number)?.toInt(),
                bounds = toGeoBoundsOrNull(row.bounds),
                featureCount = (row.featureCount as? Number)?.toInt() ?: 0
            )
        }
    }
} catch (_: Throwable) {
    null
}

private fun toGeoBoundsOrNull(value: Any?): GeoBounds? = try {
    if (value == null) {
        null
    } else {
        val row = value.asDynamic()
        GeoBounds(
            minLon = (row.minLon as Number).toDouble(),
            minLat = (row.minLat as Number).toDouble(),
            maxLon = (row.maxLon as Number).toDouble(),
            maxLat = (row.maxLat as Number).toDouble()
        )
    }
} catch (_: Throwable) {
    null
}

private fun toS57FeatureOrNull(value: Any?): S57Feature? = try {
    if (value == null) {
        null
    } else {
        val row = value.asDynamic()
        val attrs = linkedMapOf<String, S57Value>()
        val attrRows = row.attributes
        if (attrRows != null) {
            val attrLength = (attrRows.length as Number).toInt()
            for (index in 0 until attrLength) {
                val attr = attrRows[index]
                val key = attr.key as? String
                val attrValue = toS57ValueOrNull(attr.value)
                if (key != null && attrValue != null) attrs[key] = attrValue
            }
        }
        val id = (row.id as? String)?.toLongOrNull() ?: (row.id as? Number)?.toLong()
        if (id == null) {
            null
        } else {
            S57Feature(
                id = id,
                objectClass = row.objectClass as String,
                attributes = attrs,
                geometry = toS57GeometryOrNull(row.geometry) ?: S57Geometry.Empty
            )
        }
    }
} catch (_: Throwable) {
    null
}

private fun toS57ValueOrNull(value: Any?): S57Value? = try {
    if (value == null) {
        null
    } else {
        val row = value.asDynamic()
        when (row.type as? String) {
            "text" -> S57Value.Text(row.value as? String ?: "")
            "integer" -> S57Value.Integer((row.value as Number).toInt())
            "decimal" -> S57Value.Decimal((row.value as Number).toDouble())
            "list" -> {
                val valuesRow = row.values
                val length = (valuesRow.length as Number).toInt()
                S57Value.ListValue((0 until length).mapNotNull { index -> toS57ValueOrNull(valuesRow[index]) })
            }
            "empty" -> S57Value.Empty
            else -> null
        }
    }
} catch (_: Throwable) {
    null
}

private fun toS57GeometryOrNull(value: Any?): S57Geometry? = try {
    if (value == null) {
        null
    } else {
        val row = value.asDynamic()
        when (row.type as? String) {
            "empty" -> S57Geometry.Empty
            "point" -> toGeoPointOrNull(row.coordinate)?.let { coordinate -> S57Geometry.Point(coordinate) }
            "multipoint" -> S57Geometry.MultiPoint(toGeoPointList(row.points))
            "linestring" -> S57Geometry.LineString(toGeoPointList(row.points))
            "polygon" -> S57Geometry.Polygon(toGeoPointRings(row.rings))
            "multipolygon" -> {
                val polygonsRow = row.polygons
                val length = (polygonsRow.length as Number).toInt()
                S57Geometry.MultiPolygon((0 until length).mapNotNull { index -> toS57GeometryOrNull(polygonsRow[index]) as? S57Geometry.Polygon })
            }
            else -> null
        }
    }
} catch (_: Throwable) {
    null
}

private fun toGeoPointOrNull(value: Any?): GeoPoint? = try {
    if (value == null) {
        null
    } else {
        val row = value.asDynamic()
        GeoPoint((row.lon as Number).toDouble(), (row.lat as Number).toDouble())
    }
} catch (_: Throwable) {
    null
}

private fun toGeoPointList(value: Any?): List<GeoPoint> = try {
    if (value == null) {
        emptyList()
    } else {
        val rows = value.asDynamic()
        val length = (rows.length as Number).toInt()
        (0 until length).mapNotNull { index -> toGeoPointOrNull(rows[index]) }
    }
} catch (_: Throwable) {
    emptyList()
}

private fun toGeoPointRings(value: Any?): List<List<GeoPoint>> = try {
    if (value == null) {
        emptyList()
    } else {
        val rows = value.asDynamic()
        val length = (rows.length as Number).toInt()
        (0 until length).map { index -> toGeoPointList(rows[index]) }
    }
} catch (_: Throwable) {
    emptyList()
}
