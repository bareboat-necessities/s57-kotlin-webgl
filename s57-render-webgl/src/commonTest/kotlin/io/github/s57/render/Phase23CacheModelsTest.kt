package io.github.s57.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Phase23CacheModelsTest {
    @Test
    fun createsStableCacheKeyFromFileNameAndByteCount() {
        assertEquals("US5NYC.000#12345", browserChartCacheKey("US5NYC.000", 12345))
        assertEquals("unnamed#7", browserChartCacheKey("   ", 7))
    }

    @Test
    fun formatsCacheEntryLabelsAndSummary() {
        val entries = listOf(
            BrowserChartCacheEntry("b#2", "B.000", 2, "CELLB", 10, 2.0),
            BrowserChartCacheEntry("a#1", "A.000", 1, "CELLA", 5, 1.0)
        )
        val summary = browserChartCacheSummary(entries)

        assertTrue("cachedCells=2" in summary)
        assertTrue("A.000 — cell=CELLA — bytes=1 — features=5" in summary)
        assertTrue("B.000 — cell=CELLB — bytes=2 — features=10" in summary)
    }
}
