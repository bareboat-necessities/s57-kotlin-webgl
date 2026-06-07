package io.github.s57.render

import io.github.s57.core.GeoBounds
import io.github.s57.core.GeoPoint

data class ChartRenderRequest(
    val cellId: String,
    val bounds: GeoBounds,
    val widthPx: Int,
    val heightPx: Int,
    val scaleDenominator: Double,
    val paletteName: String = "DayBright",
    val camera: ChartCameraState = ChartCameraState(
        center = GeoPoint((bounds.minLon + bounds.maxLon) / 2.0, (bounds.minLat + bounds.maxLat) / 2.0),
        zoom = scaleDenominator,
        viewport = ScreenSize(widthPx, heightPx)
    ),
    val centerCrosshair: CenterCrosshairConfig = CenterCrosshairConfig(),
    val depthMesh: DepthMeshConfig = DepthMeshConfig(),
    val renderMode: ChartRenderMode = ChartRenderMode.Flat2D
)

enum class ChartRenderMode {
    Flat2D,
    Tilted2D,
    DepthMesh3D
}

data class RenderedFrameSummary(
    val widthPx: Int,
    val heightPx: Int,
    val message: String,
    val camera: ChartCameraState? = null,
    val centerCrosshairHits: List<ChartHitResult> = emptyList(),
    val depthMeshEnabled: Boolean = false
)
