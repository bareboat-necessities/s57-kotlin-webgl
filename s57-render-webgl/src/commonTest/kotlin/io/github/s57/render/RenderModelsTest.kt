package io.github.s57.render

import io.github.s57.core.GeoBounds
import kotlin.test.Test
import kotlin.test.assertEquals

class RenderModelsTest {
    @Test
    fun requestStoresStaticViewport() {
        val request = ChartRenderRequest("US5TEST", GeoBounds(-75.0, 39.0, -73.0, 41.0), 1280, 720, 40000.0)
        assertEquals(1280, request.widthPx)
    }
}
