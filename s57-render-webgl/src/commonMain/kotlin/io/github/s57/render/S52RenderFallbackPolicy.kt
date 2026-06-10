package io.github.s57.render

/**
 * Browser S-52 rendering is the preferred path, but the viewer must not leave a
 * successfully decoded/projected chart as a plain blue background when S-52
 * produces no GPU output. This small common policy keeps that decision testable
 * without depending on browser WebGL APIs.
 */
fun S52RenderSummary.needsGeometryFallback(projectedSourceFeatureCount: Int): Boolean {
    if (projectedSourceFeatureCount <= 0) return false
    if (failureStage != "none") return true
    if (commandCount <= 0) return true
    if (drawCallCount <= 0) return true
    return false
}

fun s52FallbackMessage(reason: String, fallbackMessage: String): String =
    reason + "; fallback geometry render: " + fallbackMessage
