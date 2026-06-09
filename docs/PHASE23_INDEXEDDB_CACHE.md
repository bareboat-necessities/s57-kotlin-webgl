# Phase 23 — IndexedDB browser persistence

Phase 23 adds browser-side persistence for imported ENC payloads. Imported `.000` files are stored as byte payloads in IndexedDB together with lightweight metadata. The viewer can restore cached cells after a page refresh without asking the user to pick the files again.

## Added files

```text
s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/BrowserChartCacheModels.kt
s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserChartIndexedDbCache.kt
s57-render-webgl/src/commonTest/kotlin/io/github/s57/render/Phase23CacheModelsTest.kt
```

## Updated files

```text
s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS57FileImporter.kt
demo/src/jsMain/kotlin/io/github/s57/demo/Main.kt
```

The HTML was not changed in this phase because the connector blocked the full-file HTML update. The demo injects the new cache controls from Kotlin at startup:

```text
- Restore cached cells
- Clear browser cache
- Cached cells status panel
```

## IndexedDB database

```text
database: s57-kotlin-webgl-cache
object store: encPayloads
key path: cacheKey
version: 1
```

Each row stores:

```text
cacheKey
fileName
byteCount
cellId
featureCount
cachedAtMillis
payload
```

The payload is kept as an `Int8Array` so it can be converted back to a Kotlin `ByteArray` and imported again through `S57WebGlEngine.importS57Bytes`.

## Viewer behavior

```text
- Selecting files reads bytes through BrowserS57FileImporter.readFileBytes.
- The viewer imports bytes into S57WebGlEngine.
- Successful imports are written to IndexedDB.
- Cache status is refreshed after each store/clear operation.
- Restore cached cells clears the in-memory engine and imports every cached payload.
- Clear imported cells leaves IndexedDB unchanged.
- Clear browser cache deletes IndexedDB payloads while leaving current in-memory imports unchanged.
```

## Current limitation

The cache key is `fileName#byteCount`. That is stable enough for Phase 23 but not collision-proof. A future phase should add a fast content hash when persisting real chart portfolios.
