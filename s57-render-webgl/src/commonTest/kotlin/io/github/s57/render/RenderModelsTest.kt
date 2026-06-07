package io.github.s57.render

import io.github.s57.core.GeoBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderModelsTest {
    @Test
    fun requestStoresStaticViewportAndDefaultCamera() {
        val request = ChartRenderRequest("US5TEST", GeoBounds(-75.0, 39.0, -73.0, 41.0), 1280, 720, 40000.0)
        assertEquals(1280, request.widthPx)
        assertEquals(720, request.camera.viewport.heightPx)
        assertEquals(40000.0, request.camera.zoom)
    }

    @Test
    fun requestCanEnableCrosshairTiltAndDepthMesh() {
        val request = ChartRenderRequest(
            cellId = "US5TEST",
            bounds = GeoBounds(-75.0, 39.0, -73.0, 41.0),
            widthPx = 1280,
            heightPx = 720,
            scaleDenominator = 40000.0,
            centerCrosshair = CenterCrosshairConfig(enabled = true, queryOnRender = true),
            depthMesh = DepthMeshConfig(enabled = true),
            renderMode = ChartRenderMode.DepthMesh3D
        )
        assertTrue(request.centerCrosshair.enabled)
        assertTrue(request.depthMesh.enabled)
        assertEquals(ChartRenderMode.DepthMesh3D, request.renderMode)
    }
}
