# Phase 22 — browser viewer usability

Phase 22 turns the demo from a one-button render shell into a usable browser chart viewer for imported cells.

## Added UI controls

```text
- Imported cell selector
- Cell summary panel
- Palette selector: day, dusk, dark, day black, day white
- Manual scale input
- Zoom in / zoom out buttons
- Render active cell button
- Reload selected files button
- Clear imported cells button
- Built-in sample button retained
```

## Files

```text
demo/src/jsMain/resources/index.html
demo/src/jsMain/kotlin/io/github/s57/demo/Main.kt
s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/ViewerControls.kt
s57-render-webgl/src/commonTest/kotlin/io/github/s57/render/Phase22ViewerControlsTest.kt
```

## Viewer state

`ViewerControlState` and helpers keep non-DOM behavior testable:

```text
chooseInitialActiveCell(cells, currentCellId)
viewerCellOptions(cells)
normalizePaletteName(value)
boundedScale(value)
```

The browser demo uses these helpers for active-cell choice, palette normalization, and scale/zoom bounds.

## Behavior

```text
- Importing files clears previous engine state and populates the cell selector.
- The active cell defaults to the first imported cell with bounds.
- Changing selected cell resets manual scale to auto-fit.
- Changing palette re-renders the active cell.
- Changing scale re-renders the active cell.
- Zoom buttons adjust scale denominator by 1.6x.
- Reload re-imports the currently selected browser File objects.
- Clear resets imported cells, failures, selected files, and scale override.
```

## Diagnostics visible in browser

The viewer displays:

```text
- active cell id
- cell name
- feature count
- bounds
- indexed feature count
- raw feature/vector counts when available
- decoded feature count
- geometry diagnostics
- selected palette
- selected/auto scale
- Phase16/20 viewport diagnostics
- S-52 render summary
```

## Acceptance

A user can now import multiple local ENC files, choose an active cell, inspect its bounds/features, switch palettes, zoom/scale, clear, and reload without editing code or refreshing the browser page.
