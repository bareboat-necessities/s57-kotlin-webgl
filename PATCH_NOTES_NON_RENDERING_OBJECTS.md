# Non-rendering objects browser fix

Incremental patch for the blue-canvas/no-visible-objects browser failure.

## Changes

- Browser uploaded `.000` payloads are copied from `ArrayBuffer`/`Int8Array` byte-by-byte instead of using an unsafe JS array cast.
- The packaged demo tries to auto-load `data/statue-liberty.000` on startup when no user/cached cell is already loaded.
- All S-52 render entry points route through the structured render wrapper.
- If S-52 portrayal or WebGL produces zero draw calls for a projected chart, the viewer now falls back to the existing simple geometry renderer instead of leaving only the cleared blue background.
- The fallback renderer can reuse an existing WebGL2 canvas context, so it still works after the S-52 renderer has already initialized WebGL2.
- Added common tests for the fallback policy.

## Validation note

This sandbox did not include a Gradle wrapper or Gradle installation, so I could not run the Kotlin/JS Gradle build locally here. The patch is limited to the files in this zip and should be applied over the uploaded baseline.
