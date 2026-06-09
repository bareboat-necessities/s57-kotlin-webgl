package io.github.s57.render

import io.github.s57.core.S57CellSummary
import kotlin.math.max
import kotlin.math.min

/** Phase 22 browser-viewer control state independent from the DOM. */
data class ViewerControlState(
    val activeCellId: String? = null,
    val paletteName: String = "daybright",
    val scaleDenominator: Double? = null,
    val minScaleDenominator: Double = 500.0,
    val maxScaleDenominator: Double = 50_000_000.0
) {
    fun selectCell(cellId: String?): ViewerControlState = copy(activeCellId = cellId?.takeIf { it.isNotBlank() }, scaleDenominator = null)

    fun selectPalette(value: String): ViewerControlState = copy(paletteName = normalizePaletteName(value))

    fun setScale(value: Double?): ViewerControlState = copy(
        scaleDenominator = value?.coerceIn(minScaleDenominator, maxScaleDenominator)
    )

    fun zoomIn(defaultScale: Double): ViewerControlState = setScale((scaleDenominator ?: defaultScale) / 1.6)

    fun zoomOut(defaultScale: Double): ViewerControlState = setScale((scaleDenominator ?: defaultScale) * 1.6)
}

data class ViewerCellOption(
    val cellId: String,
    val label: String,
    val hasBounds: Boolean,
    val featureCount: Int
)

fun viewerCellOptions(cells: List<S57CellSummary>): List<ViewerCellOption> = cells.map { cell ->
    ViewerCellOption(
        cellId = cell.cellId,
        label = cell.cellId + " — features=" + cell.featureCount + if (cell.bounds == null) " — no bounds" else "",
        hasBounds = cell.bounds != null,
        featureCount = cell.featureCount
    )
}

fun chooseInitialActiveCell(cells: List<S57CellSummary>, currentCellId: String? = null): String? {
    if (currentCellId != null && cells.any { it.cellId == currentCellId }) return currentCellId
    return cells.firstOrNull { it.bounds != null }?.cellId ?: cells.firstOrNull()?.cellId
}

fun normalizePaletteName(value: String): String = when (value.trim().lowercase()) {
    "day", "daybright", "bright" -> "daybright"
    "dusk" -> "dusk"
    "night", "dark" -> "dark"
    "black", "dayblack", "dayblackback" -> "dayblackback"
    "white", "daywhite", "daywhiteback" -> "daywhiteback"
    else -> "daybright"
}

fun boundedScale(value: Double, minValue: Double = 500.0, maxValue: Double = 50_000_000.0): Double = min(max(value, minValue), maxValue)
