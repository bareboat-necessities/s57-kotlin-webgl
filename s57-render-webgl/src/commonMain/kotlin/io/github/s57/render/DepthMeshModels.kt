package io.github.s57.render

import io.github.s57.core.GeoBounds
import io.github.s57.core.GeoPoint

/** One sample for future depth mesh construction. Positive depth is below chart datum. */
data class DepthSample(
    val coordinate: GeoPoint,
    val depthMeters: Double,
    val sourceFeatureId: Long? = null
)

data class DepthMeshVertex(
    val x: Float,
    val y: Float,
    val z: Float,
    val depthMeters: Float
)

data class DepthMeshTile(
    val bounds: GeoBounds,
    val vertices: List<DepthMeshVertex>,
    val triangleIndices: List<Int>
) {
    init {
        require(triangleIndices.size % 3 == 0) { "triangleIndices must describe complete triangles" }
    }
}

interface DepthMeshBuilder {
    fun build(samples: List<DepthSample>, request: ChartRenderRequest): DepthMeshTile?
}

class EmptyDepthMeshBuilder : DepthMeshBuilder {
    override fun build(samples: List<DepthSample>, request: ChartRenderRequest): DepthMeshTile? = null
}
