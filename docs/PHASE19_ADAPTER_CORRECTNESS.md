# Phase 19 — S-57 to S-52 adapter correctness

Phase 19 hardens the S-57 to S-52 preparation path so common ENC features do not collapse into one generic or incomplete symbol path.

## Goals

```text
- Do not flatten MultiPolygon to the first polygon.
- Do not keep SOUNDG MultiPoint as one unsplit sounding feature.
- Preserve per-sounding VALSOU values.
- Normalize numeric text attributes into numeric portrayal values where known.
- Count unsupported object classes separately from unsupported attributes.
- Keep DEPARE, DEPCNT, SOUNDG, and BOYLAT mapped to distinct primitive paths.
```

## Common adapter changes

```text
s57-s52-adapter/src/commonMain/kotlin/io/github/s57/adapter/S57ToS52Adapter.kt
```

The adapter now:

```text
- Splits MultiPolygon into multiple S57PortrayalFeature objects.
- Splits SOUNDG MultiPoint into one point feature per sounding.
- Carries sourceFeatureId so split features remain traceable to the source S-57 feature.
- Converts numeric text values for DRVAL1, DRVAL2, HEIGHT, VALDCO, VALSOU, SCAMIN, SCAMAX, and common category/color fields.
- Emits S57ToS52DiagnosticKind.UnsupportedObjectClass and UnsupportedAttribute separately.
```

## Browser S-52 bridge changes

```text
s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS52Bridge.kt
```

The browser bridge now performs the same split before creating real S-52 `EncFeature` values. This is important because the current browser render path talks directly to S-52 from `S57Feature` values.

## Tests

```text
s57-s52-adapter/src/commonTest/kotlin/io/github/s57/adapter/Phase19AdapterRegressionTest.kt
```

The Phase 19 tests cover:

```text
- MultiPolygon split instead of flattening.
- SOUNDG MultiPoint split with per-point VALSOU.
- Separate unsupported object/attribute counters.
- DEPARE -> Area, DEPCNT -> Line, SOUNDG -> Point, BOYLAT -> Point.
```

## Still left

The S-52 command-level assertions for real `S52DrawCommand` output remain browser/JS-side integration work because the real S-52 renderer is consumed from `s57-render-webgl/jsMain`. The Phase 19 common tests protect the adapter boundary; the browser bridge patch makes the same split behavior affect the real render path.
