# Interaction and declutter performance update

This update targets browser zoom/scroll performance without adding Canvas2D or decoded-geometry fallbacks.

## Interaction render cadence

- Replaced pure debounce rendering for drag/wheel with a coalesced throttle.
- Wheel/drag events still update the chart camera immediately, but only one S-52 render is queued at a time.
- Interactive redraws are capped to roughly one frame per 85 ms instead of repeatedly cancelling and restarting a timer on every wheel tick.

## Scale-dependent S-52 declutter

`BrowserS52DisplayCommandFilter` now selects a scale profile from the current scale denominator:

- `detail`
- `approach`
- `harbor`
- `harbor-overview`
- `overview`

The display planner now performs screen-space thinning before GPU rendering:

- point symbols are thinned by screen tile while keeping higher-priority / navigationally important symbols;
- text labels are thinned by screen tile and non-critical long labels are suppressed in overview profiles;
- soundings are thinned by screen tile;
- vector area patterns are capped/tiled in overview profiles so pattern fills do not dominate the frame;
- raster-backed area patterns remain suppressed in the strict WebGL-only path.

New diagnostics are exported in render messages and on `window`:

- `s57S52DeclutterProfile`
- `s57S52SuppressedScalePointSymbolCount`
- `s57S52SuppressedTextDeclutterCount`
- `s57S52SuppressedSoundingDeclutterCount`
- `s57S52SuppressedVectorAreaPatternDeclutterCount`

## Text collision performance

The WebGL text postpass now uses a spatial occupancy grid for label collision checks instead of scanning every placed label. This reduces label placement cost from broad O(n²) behavior to mostly local tile checks on dense charts.

## Tests

Added a display-plan test covering overview-scale vector-pattern decluttering.
