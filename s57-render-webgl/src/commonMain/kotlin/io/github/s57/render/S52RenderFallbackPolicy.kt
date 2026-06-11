package io.github.s57.render

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
 * Even when S-52 reports successful draw calls, the bundled/development S-52
 * WebGL path can visually degrade to marker dots if symbol/line resources are
 * incomplete or if the renderer counts commands before real path geometry is
 * emitted. Keep decoded ENC geometry and object-symbol hints visible as a
 * browser overlay whenever real chart features exist and S-52 reached the GPU.
 */
fun S52RenderSummary.shouldOverlayDecodedGeometry(
    projectedSourceFeatureCount: Int,
    projectedLinearOrAreaFeatureCount: Int
): Boolean {
    if (projectedSourceFeatureCount <= 0) return false
    if (failureStage != "none") return false
    if (drawCallCount <= 0) return false
    return true
}

fun s52FallbackMessage(reason: String, fallbackMessage: String): String =
    reason + "; fallback geometry render: " + fallbackMessage
