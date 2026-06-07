package io.github.s57.render

import kotlinx.browser.document
import org.khronos.webgl.WebGLRenderingContext
import org.w3c.dom.HTMLCanvasElement

class BrowserS57WebGlRenderer {
    fun renderPlaceholder(canvasId: String): RenderedFrameSummary {
        val canvas = document.getElementById(canvasId) as? HTMLCanvasElement
            ?: return RenderedFrameSummary(0, 0, "Canvas '$canvasId' not found")
        val gl = canvas.getContext("webgl") as? WebGLRenderingContext
            ?: return RenderedFrameSummary(canvas.width, canvas.height, "WebGL is not available")

        gl.viewport(0, 0, canvas.width, canvas.height)
        gl.clearColor(0.06f, 0.10f, 0.16f, 1.0f)
        gl.clear(WebGLRenderingContext.COLOR_BUFFER_BIT)
        return RenderedFrameSummary(canvas.width, canvas.height, "Phase 0 WebGL canvas initialized")
    }
}
