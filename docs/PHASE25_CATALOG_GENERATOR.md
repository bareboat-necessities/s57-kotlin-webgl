# Phase 25 — generated-style S-57 catalogue hardening

Phase 25 replaces the tiny hand-seeded raw-decoder lookup table with a generated-style catalogue table sourced from the OpenCPN S-57 CSV inventory used by the companion S-52 project.

## What changed

```text
s57-core/src/commonMain/kotlin/io/github/s57/core/raw/S57CatalogLookups.kt
tools/generate_s57_catalog_lookup.py
s57-core/src/commonTest/kotlin/io/github/s57/core/raw/S57CatalogLookupsTest.kt
```

## Why this matters

Earlier phases only knew the object/attribute classes needed for smoke tests and early NOAA chart rendering. That created two problems:

```text
- Many valid S-57 objects decoded as OBJL_### even when the code was standard.
- Several attribute codes were shifted because the small seed table did not match the full CSV catalogue.
```

Phase 25 broadens the decoder lookup coverage for common base objects, meta objects, collection objects, and base attributes used by NOAA/ENC cells.

## Generator

The generator accepts OpenCPN-style CSV files:

```bash
python3 tools/generate_s57_catalog_lookup.py \
  --objects /path/to/s57objectclasses.csv \
  --attributes /path/to/s57attributes.csv \
  --output s57-core/src/commonMain/kotlin/io/github/s57/core/raw/S57CatalogLookups.kt
```

Expected CSV headers:

```text
s57objectclasses.csv: Code,ObjectClass,Acronym,...
s57attributes.csv:    Code,Attribute,Acronym,...
```

## Lookup behavior

The decoder remains safe for unknown catalogue rows:

```text
unknown object class code -> OBJL_###
unknown attribute code    -> ATTL_###
```

Reverse lookup is case-insensitive:

```kotlin
S57ObjectClassLookup.code("soundg") == 129
S57AttributeLookup.code("watlev") == 187
```

## Test coverage

Phase 25 adds tests for:

```text
- expanded object class count
- common object class mappings: ACHARE, FAIRWY, HRBARE, PILPNT, SBDARE, SOUNDG
- meta/collection mappings: M_COVR, C_AGGR
- corrected attribute mappings: CATACH, CATOBS, DRVAL1, QUASOU, SCAMIN, TECSOU, VALSOU, WATLEV
- stable unknown fallback strings
```

## Remaining limitation

The committed lookup table intentionally covers the base and meta/collection range needed by the current renderer. The generator can emit the full OpenCPN inventory, including private/extended classes, when the project decides to carry the full table in source.
