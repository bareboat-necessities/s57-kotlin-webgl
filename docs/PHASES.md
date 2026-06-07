# Phases

## Phase 0 — repository skeleton

Create the modules, build, CI, docs, and browser demo shell.

## Phase 1 — ISO8211 parser

Parse leaders, directories, DDR records, fields, and subfields.

## Phase 2 — S-57 raw decoder

Decode DSID/DSSI, feature records, vector records, attributes, and object classes.

## Phase 3 — geometry reconstruction

Build points, soundings, lines, and area rings from S-57 topology.

## Phase 4 — browser index

Store decoded chart cells, features, geometry, and spatial bins in a browser-oriented index layer.

## Phase 5 — S-52 adapter

Convert decoded features to the input model consumed by `s52-kotlin-webgl`.

## Phase 6 — static WebGL render

Render a selected cell or fixed bounds to a WebGL canvas.

## Phase 7 — diagnostics and artifacts

Export parser/index/portrayal/render diagnostics that catch empty rendering and fallback explosions.
