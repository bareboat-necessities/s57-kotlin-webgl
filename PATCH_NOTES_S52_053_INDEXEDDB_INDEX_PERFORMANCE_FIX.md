# S-52 0.5.3 IndexedDB / index performance fix

This incremental patch addresses the slow browser demo path where the chart was
still doing too much work after the S-52 0.5.3 upgrade.

## Changes

- Keeps one IndexedDB connection open in `BrowserChartIndexedDbCache` instead of
  reopening the database for every list/load/put operation.
- Bumps the decoded-object cache schema to version 2 and adds `cellId` and
  `fileName` indexes for future targeted cache reads.
- Before decoding an imported NOAA chart group, checks the decoded IndexedDB
  dataset cache by the same `fileName#byteCount` key used by the cache writer.
  Re-importing the same ENC can now restore decoded chart objects directly and
  rebuild only the in-memory spatial index.
- Fixes the in-memory spatial index query path so an empty bin hit no longer
  falls back to scanning every feature in the cell.
- Adds an LRU-style candidate-bin cache in the in-memory index store. During
  pan/zoom, repeated viewport queries that touch the same spatial bins reuse the
  candidate feature list and only redo the final bounds/object-class filter.
- Adds an S-52 portrayal cache for repeated pan redraws with the same feature set,
  palette, and rounded scale bucket. Cache hits skip S-57-to-S-52 adaptation and
  OpenCPN/S-52 CSP portrayal, then only redraw the cached commands with the new
  viewport.
- Suppresses repeated console logging of the same cached S-52 diagnostics on
  redraw; diagnostics are logged only when portrayal is actually recomputed.

## Notes

IndexedDB is asynchronous, so it is still not used directly inside every render
frame. The correct hot path is:

1. IndexedDB restores decoded objects at import/restore time.
2. The active cells are rebuilt into the synchronous in-memory spatial index.
3. Pan/zoom uses that in-memory index plus candidate/S-52 portrayal caches.

This avoids blocking the browser event loop on IndexedDB transactions during
interactive rendering.
