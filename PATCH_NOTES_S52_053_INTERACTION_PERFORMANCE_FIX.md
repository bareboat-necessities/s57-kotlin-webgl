# S-52 0.5.3 browser interaction performance fix

This incremental patch reduces browser stalls during pan and zoom in the demo.

## Problem

The demo was doing full S-57 query + S-52 portrayal + WebGL draw work too often:

- each wheel/drag event triggered an immediate full render;
- zoom buttons called the full `renderActive()` path;
- `renderActive()` rebuilt the chart request and caused several render passes (`ShowCharts`, `SetPalette`, `SetScale`, `Render`) for a single user action;
- the S-52 bridge/session and WebGL renderer were recreated on every render.

For an imported NOAA cell this can mean thousands of S-52 commands per event, so zooming and panning felt very slow.

## Changes

- Adds optional `redraw` flags to interactive `ChartCanvasCommand` values.
- Allows `Zoom`, `Scroll`, `SetScale`, `SetPalette`, `SetView`, and `ShowCharts` to update view state without immediately rendering.
- Coalesces mouse-wheel, drag, and zoom-button events in the browser demo with a short debounce.
- Changes normal demo `renderCell()` to perform exactly one final render after setup instead of several intermediate renders.
- Reuses one `BrowserS52Bridge` and caches the `WebGlS52Renderer` per canvas instead of recreating them for every frame.
- Disables center-crosshair hit-query-on-render to avoid extra hit testing during continuous interaction.
- Adds a regression test for coalesced interactive commands.

## Expected effect

Zoom and pan should no longer queue one heavy S-52 render per raw pointer/wheel event.  The canvas redraws after the interaction burst settles, and repeated zoom-button clicks are collapsed into one render.
