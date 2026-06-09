package io.github.s57.render

data class PerformanceMetricSample(
    val name: String,
    val elapsedMs: Double
)

data class PerformanceMetricSummary(
    val name: String,
    val count: Int,
    val minMs: Double,
    val maxMs: Double,
    val avgMs: Double
) {
    fun toPlainText(): String = name + " count=" + count + " minMs=" + minMs.roundTo(3) + " maxMs=" + maxMs.roundTo(3) + " avgMs=" + avgMs.roundTo(3)
}

class PerformanceMetricCollector {
    private val samples = mutableListOf<PerformanceMetricSample>()

    fun add(name: String, elapsedMs: Double) {
        samples += PerformanceMetricSample(name, elapsedMs.coerceAtLeast(0.0))
    }

    fun addTiming(prefix: String, timing: EngineTimingReport) {
        add(prefix + ".decode", timing.decodeMs)
        add(prefix + ".index", timing.indexMs)
        add(prefix + ".framePrepare", timing.framePrepareMs)
        add(prefix + ".artifactAnalyze", timing.artifactAnalyzeMs)
        add(prefix + ".total", timing.totalMs)
    }

    fun summaries(): List<PerformanceMetricSummary> = samples
        .groupBy { it.name }
        .map { (name, values) ->
            val elapsed = values.map { it.elapsedMs }
            PerformanceMetricSummary(
                name = name,
                count = elapsed.size,
                minMs = elapsed.minOrNull() ?: 0.0,
                maxMs = elapsed.maxOrNull() ?: 0.0,
                avgMs = if (elapsed.isEmpty()) 0.0 else elapsed.sum() / elapsed.size
            )
        }
        .sortedBy { it.name }

    fun toPlainText(): String = buildString {
        appendLine("performanceMetrics samples=" + samples.size)
        summaries().forEach { appendLine(it.toPlainText()) }
    }

    fun clear() {
        samples.clear()
    }
}
