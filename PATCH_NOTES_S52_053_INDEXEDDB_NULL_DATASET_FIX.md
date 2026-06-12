# S-52 0.5.3 IndexedDB null dataset restore fix

This incremental patch fixes a browser/CI crash introduced by the IndexedDB
object-cache restore path.

## Problem

On a cold cache, `store.get(cacheKey)` legitimately returns `null`. The previous
Kotlin/JS code then evaluated an extension call through a `dynamic` receiver:

```kotlin
row?.asDynamic()?.dataset.toS57DatasetOrNull()
```

In the generated JavaScript this can become a dynamic method lookup on `null`,
which raises:

```text
Cannot read properties of null (reading 'toS57DatasetOrNull')
```

Because the exception happened inside the IndexedDB success callback before the
normal fallback path, the import never continued to byte decoding and the Phase
26 snapshot waited until timeout.

## Fix

* Treat missing IndexedDB rows as a normal cache miss.
* Convert the dynamic dataset row into an `Any?` local before conversion.
* Replace private dynamic extension helpers with regular top-level helper
  functions, so Kotlin/JS does not emit dynamic method calls such as
  `.toS57DatasetOrNull()` on IndexedDB objects or nulls.
* Preserve the intended behavior: cold cache falls back to decode; warm cache
  restores decoded chart objects.

## Files changed

* `s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserChartIndexedDbCache.kt`
