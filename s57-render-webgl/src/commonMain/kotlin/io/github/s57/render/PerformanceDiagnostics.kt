package io.github.s57.render

/** Phase 24 deterministic timing bundle. Values are milliseconds. */
data class EngineTimingReport(
    val decodeMs: Double = 0.0,
    val indexMs: Double = 0.0,
    val framePrepareMs: Double = 0.0,
    val artifactAnalyzeMs: Double = 0.0,
    val totalMs: Double = decodeMs + indexMs + framePrepareMs + artifactAnalyzeMs
) {
    fun rounded(decimals: Int = 3): EngineTimingReport = copy(
        decodeMs = decodeMs.roundTo(decimals),
        indexMs = indexMs.roundTo(decimals),
        framePrepareMs = framePrepareMs.roundTo(decimals),
        artifactAnalyzeMs = artifactAnalyzeMs.roundTo(decimals),
        totalMs = totalMs.roundTo(decimals)
    )

    fun toPlainText(prefix: String = "timing"): String = rounded().let { timing ->
        prefix + " decodeMs=" + timing.decodeMs +
            " indexMs=" + timing.indexMs +
            " framePrepareMs=" + timing.framePrepareMs +
            " artifactAnalyzeMs=" + timing.artifactAnalyzeMs +
            " totalMs=" + timing.totalMs
    }
}

data class PerformanceFrameReport(
    val cellId: String,
    val featureCount: Int,
    val onscreenFeatureCount: Int,
    val offscreenFeatureCount: Int,
    val scaleDenominator: Double,
    val timing: EngineTimingReport
) {
    fun toPlainText(): String = buildString {
        appendLine("performance cell=$cellId features=$featureCount onscreen=$onscreenFeatureCount offscreen=$offscreenFeatureCount scale=$scaleDenominator")
        appendLine(timing.toPlainText())
    }
}

fun performanceFrameReport(result: S57EngineRenderResult): PerformanceFrameReport = PerformanceFrameReport(
    cellId = result.frame.request.cellId,
    featureCount = result.frame.projectedFeatures.size,
    onscreenFeatureCount = result.diagnostics.onscreenFeatureCount,
    offscreenFeatureCount = result.diagnostics.offscreenFeatureCount,
    scaleDenominator = result.frame.request.scaleDenominator,
    timing = result.timing
)

expect fun monotonicNowMs(): Double

internal fun Double.roundTo(decimals: Int): Double {
    val scale = pow10(decimals)
    return kotlin.math.round(this * scale) / scale
}

private fun pow10(decimals: Int): Double {
    var value = 1.0
    repeat(decimals.coerceAtLeast(0)) { value *= 10.0 }
    return value
}
