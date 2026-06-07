package io.github.s57.render

import io.github.s57.core.GeoBounds
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DepthMeshModelsTest {
    @Test
    fun meshTileRequiresCompleteTriangles() {
        assertFailsWith<IllegalArgumentException> {
            DepthMeshTile(GeoBounds(-1.0, -1.0, 1.0, 1.0), emptyList(), listOf(0, 1))
        }
    }

    @Test
    fun depthConfigRejectsInvalidGrid() {
        assertFailsWith<IllegalArgumentException> {
            DepthMeshConfig(enabled = true, gridCellPx = 0)
        }
    }
}
