package io.github.s57.render

import io.github.s57.core.GeoBounds

data class ChartRenderRequest(
    val cellId: String,
    val bounds: GeoBounds,
    val widthPx: Int,
    val heightPx: Int,
    val scaleDenominator: Double,
    val paletteName: String = "DayBright"
)

data class RenderedFrameSummary(
    val widthPx: Int,
    val heightPx: Int,
    val message: String
)
