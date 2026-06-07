package io.github.s57.render

import kotlinx.browser.document
import org.khronos.webgl.WebGLRenderingContext
import org.w3c.dom.HTMLCanvasElement

class BrowserS57WebGlRenderer(
    private val hitTester: ChartHitTester = EmptyChartHitTester()
) {
    fun renderPlaceholder(canvasId: String): RenderedFrameSummary {
        val canvas = document.getElementById(canvasId) as? HTMLCanvasElement
            ?: return RenderedFrameSummary(0, 0, "Canvas '$canvasId' not found")
        val gl = canvas.getContext("webgl") as? WebGLRenderingContext
            ?: return RenderedFrameSummary(canvas.width, canvas.height, "WebGL is not available")

        gl.viewport(0, 0, canvas.width, canvas.height)
        gl.clearColor(0.06f, 0.10f, 0.16f, 1.0f)
        gl.clear(WebGLRenderingContext.COLOR_BUFFER_BIT)
        return RenderedFrameSummary(canvas.width, canvas.height, "Phase 1 WebGL canvas initialized")
    }

    fun renderStatic(canvasId: String, request: ChartRenderRequest): RenderedFrameSummary {
        val canvas = document.getElementById(canvasId) as? HTMLCanvasElement
            ?: return RenderedFrameSummary(0, 0, "Canvas '$canvasId' not found", request.camera)
        val gl = canvas.getContext("webgl") as? WebGLRenderingContext
            ?: return RenderedFrameSummary(canvas.width, canvas.height, "WebGL is not available", request.camera)

        gl.viewport(0, 0, canvas.width, canvas.height)
        val depthTint = if (request.depthMesh.enabled) 0.20f else 0.16f
        val tiltTint = (request.camera.tiltDegrees / 65.0).toFloat().coerceIn(0.0f, 1.0f) * 0.10f
        gl.clearColor(0.04f + tiltTint, 0.08f + tiltTint, depthTint, 1.0f)
        gl.clear(WebGLRenderingContext.COLOR_BUFFER_BIT)

        val hits = if (request.centerCrosshair.enabled && request.centerCrosshair.queryOnRender) {
            hitTester.centerCrosshairHitTest(request.camera)
        } else {
            emptyList()
        }
        val mode = when {
            request.depthMesh.enabled || request.renderMode == ChartRenderMode.DepthMesh3D -> "depth mesh 3D"
            request.camera.tiltDegrees > 0.0 || request.renderMode == ChartRenderMode.Tilted2D -> "tilted chart"
            else -> "flat chart"
        }
        return RenderedFrameSummary(
            widthPx = canvas.width,
            heightPx = canvas.height,
            message = "Phase 1 static $mode render placeholder",
            camera = request.camera,
            centerCrosshairHits = hits,
            depthMeshEnabled = request.depthMesh.enabled
        )
    }
}
