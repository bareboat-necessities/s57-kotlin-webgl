package io.github.s57.render

val S52RenderSummary.usedGeometryFallback: Boolean
    get() = diagnostics.any { it.code == "s52.geometry_fallback" }

/**
 * Browser S-52 rendering is the preferred path, but the viewer must not leave a
 * successfully decoded/projected chart as a plain blue background when S-52
 * produces no GPU output. This small common policy keeps that decision testable
 * without depending on browser WebGL APIs.
 */
fun S52RenderSummary.needsGeometryFallback(projectedSourceFeatureCount: Int): Boolean =
    needsGeometryFallback(projectedSourceFeatureCount = projectedSourceFeatureCount, projectedLinearOrAreaFeatureCount = 0)

fun S52RenderSummary.needsGeometryFallback(
    projectedSourceFeatureCount: Int,
    projectedLinearOrAreaFeatureCount: Int
): Boolean {
    if (projectedSourceFeatureCount <= 0) return false
    if (failureStage != "none") return true
    if (commandCount <= 0) return true
    if (drawCallCount <= 0) return true
    if (projectedLinearOrAreaFeatureCount > 0 && hasOnlyPointLikeCommands()) return true
    return false
}

fun S52RenderSummary.hasOnlyPointLikeCommands(): Boolean {
    if (commandCount <= 0) return false
    if (areaCommandCount > 0 || lineCommandCount > 0) return false
    return symbolCommandCount + textCommandCount + soundingCommandCount >= commandCount
}

/**
 * Do not draw the decoded-geometry debug overlay over a successful S-52 frame.
 *
 * The overlay uses simplified in-repository colors, line styles, point glyphs,
 * and segment-rendered sounding labels.  Those are useful as a last-resort
 * fallback when S-52 cannot draw, but they must not cover the OpenCPN
 * presentation-library symbols after S-52 has produced GPU output.  Painting the
 * overlay on top of successful S-52 frames makes the browser view diverge from
 * the selected OpenCPN symbology pack and also exposes browser-dependent text
 * and color differences.
 */
@Suppress("UNUSED_PARAMETER")
fun S52RenderSummary.shouldOverlayDecodedGeometry(
    projectedSourceFeatureCount: Int,
    projectedLinearOrAreaFeatureCount: Int
): Boolean = false

fun s52FallbackMessage(reason: String, fallbackMessage: String): String =
    reason + "; fallback geometry render: " + fallbackMessage
