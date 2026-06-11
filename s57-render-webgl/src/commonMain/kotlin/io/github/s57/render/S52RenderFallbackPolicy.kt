package io.github.s57.render

val S52RenderSummary.usedGeometryFallback: Boolean
    get() = diagnostics.any { it.code == "s52.geometry_fallback" }

/**
 * S-52/OpenCPN rendering is the authoritative browser chart presentation.
 *
 * The decoded-geometry renderer in this project deliberately uses simplified
 * debug glyphs and colors so parser/projection work can be inspected.  It must
 * not be promoted to an automatic browser fallback once the S-52 route has
 * decoded the chart and produced GPU output, because doing so draws the red
 * diamonds/crosses seen in snapshots instead of the S-52/OpenCPN portrayal.
 */
fun S52RenderSummary.needsGeometryFallback(projectedSourceFeatureCount: Int): Boolean =
    needsGeometryFallback(projectedSourceFeatureCount = projectedSourceFeatureCount, projectedLinearOrAreaFeatureCount = 0)

@Suppress("UNUSED_PARAMETER")
fun S52RenderSummary.needsGeometryFallback(
    projectedSourceFeatureCount: Int,
    projectedLinearOrAreaFeatureCount: Int
): Boolean {
    if (projectedSourceFeatureCount <= 0) return false
    if (failureStage != "none") return true
    if (commandCount <= 0) return true
    if (drawCallCount <= 0) return rasterCommandCount <= 0
    return false
}

fun S52RenderSummary.hasOnlyPointLikeCommands(): Boolean {
    if (commandCount <= 0) return false
    if (areaCommandCount > 0 || lineCommandCount > 0) return false
    return symbolCommandCount + textCommandCount + soundingCommandCount >= commandCount
}

/**
 * Do not draw decoded-geometry debug glyphs over a successful S-52 frame.
 */
@Suppress("UNUSED_PARAMETER")
fun S52RenderSummary.shouldOverlayDecodedGeometry(
    projectedSourceFeatureCount: Int,
    projectedLinearOrAreaFeatureCount: Int
): Boolean = false

fun s52FallbackMessage(reason: String, fallbackMessage: String): String =
    reason + "; fallback geometry render: " + fallbackMessage
