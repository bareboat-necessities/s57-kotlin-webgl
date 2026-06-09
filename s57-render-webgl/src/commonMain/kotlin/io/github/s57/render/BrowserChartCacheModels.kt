package io.github.s57.render

/** Metadata for one persisted browser chart payload. */
data class BrowserChartCacheEntry(
    val cacheKey: String,
    val fileName: String,
    val byteCount: Int,
    val cellId: String,
    val featureCount: Int,
    val cachedAtMillis: Double
) {
    fun label(): String = "$fileName — cell=$cellId — bytes=$byteCount — features=$featureCount"
}

fun browserChartCacheKey(fileName: String, byteCount: Int): String = fileName.trim().ifEmpty { "unnamed" } + "#" + byteCount

fun browserChartCacheSummary(entries: List<BrowserChartCacheEntry>): String = buildString {
    appendLine("cachedCells=" + entries.size)
    entries.sortedBy { it.fileName }.forEach { entry -> appendLine(entry.label()) }
}
