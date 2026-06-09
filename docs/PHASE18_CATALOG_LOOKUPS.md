# Phase 18 — generated-style S-57 catalogue lookup

Phase 18 expands the raw decoder lookup layer so common NOAA ENC objects and attributes decode to stable S-57 acronyms instead of numeric placeholders.

## Files

```text
s57-core/src/commonMain/kotlin/io/github/s57/core/raw/S57CatalogLookups.kt
s57-core/src/commonTest/kotlin/io/github/s57/core/raw/Phase18CatalogLookupTest.kt
s57-core/src/commonTest/kotlin/io/github/s57/core/raw/Phase18RawDecoderLookupTest.kt
```

## Object classes covered

The Phase 18 tests require these common NOAA/S-52 object classes to resolve:

```text
DEPARE
DEPCNT
SOUNDG
BOYLAT
BCNLAT
LIGHTS
WRECKS
OBSTRN
LNDARE
M_COVR
COALNE
SLCONS
```

Unknown object codes still decode as:

```text
OBJL_###
```

## Attributes covered

The table now includes the common portrayal/depth/light/text attributes already used by the S-52 adapter path:

```text
CATOBS
CATLAM
CATREA
CATWRK
COLOUR
COLPAT
DRVAL1
DRVAL2
HEIGHT
INFORM
LITCHR
OBJNAM
SIGGRP
SIGPER
TXTDSC
VALDCO
VALSOU
WATLEV
```

Unknown attribute codes still decode as:

```text
ATTL_###
```

## Why this matters

The S-52 adapter and browser portrayal bridge work from acronyms, not raw numeric object codes. Before Phase 18, several common NOAA classes could pass through the pipeline as `OBJL_###`, causing the S-52 bridge to reject them as unsupported object classes.

Phase 18 keeps the decoder behavior safe: common objects/attributes are named, while unknown values remain visible and stable for diagnostics.

## Later hardening

A later hardening phase should replace this curated generated-style table with a true catalogue-generation task from a checked-in machine-readable S-57 catalogue source.
