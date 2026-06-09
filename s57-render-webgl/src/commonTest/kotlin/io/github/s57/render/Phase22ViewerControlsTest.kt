package io.github.s57.render

import io.github.s57.core.GeoBounds
import io.github.s57.core.S57CellSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Phase22ViewerControlsTest {
    @Test
    fun choosesFirstCellWithBoundsAsInitialActiveCell() {
        val cells = listOf(
            S57CellSummary("NOBOUNDS", "NOBOUNDS", bounds = null, featureCount = 5),
            S57CellSummary("US5P22", "US5P22", bounds = GeoBounds(-75.0, 39.0, -73.0, 41.0), featureCount = 42)
        )

        assertEquals("US5P22", chooseInitialActiveCell(cells))
        assertEquals("NOBOUNDS", chooseInitialActiveCell(cells, currentCellId = "NOBOUNDS"))
    }

    @Test
    fun buildsCellSelectorLabelsWithBoundsState() {
        val options = viewerCellOptions(
            listOf(
                S57CellSummary("A", "A", bounds = null, featureCount = 1),
                S57CellSummary("B", "B", bounds = GeoBounds(-1.0, -1.0, 1.0, 1.0), featureCount = 2)
            )
        )

        assertEquals("A", options[0].cellId)
        assertFalse(options[0].hasBounds)
        assertTrue("no bounds" in options[0].label)
        assertTrue(options[1].hasBounds)
        assertTrue("features=2" in options[1].label)
    }

    @Test
    fun normalizesPaletteAndBoundsZoomScale() {
        val state = ViewerControlState()
            .selectPalette("night")
            .setScale(100.0)
            .zoomOut(defaultScale = 40_000.0)
            .zoomIn(defaultScale = 40_000.0)

        assertEquals("dark", state.paletteName)
        assertTrue(state.scaleDenominator != null)
        assertTrue(state.scaleDenominator!! >= 500.0)
        assertEquals("daybright", normalizePaletteName("unknown"))
        assertEquals(50_000_000.0, boundedScale(90_000_000.0))
    }

    @Test
    fun selectingNewCellClearsManualScaleOverride() {
        val state = ViewerControlState(activeCellId = "A", scaleDenominator = 22000.0)
            .selectCell("B")

        assertEquals("B", state.activeCellId)
        assertEquals(null, state.scaleDenominator)
    }
}
