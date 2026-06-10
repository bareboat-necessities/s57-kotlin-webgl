package io.github.s57.render

import kotlinx.browser.document
import org.khronos.webgl.Float32Array
import org.khronos.webgl.WebGLBuffer
import org.khronos.webgl.WebGLProgram
import org.khronos.webgl.WebGLRenderingContext
import org.khronos.webgl.WebGLShader
import org.khronos.webgl.WebGLUniformLocation
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

    fun renderS52Frame(canvasId: String, frame: StaticChartFrame): RenderedFrameSummary =
        renderS52FrameWithSummary(canvasId, frame)

    fun renderFrame(canvasId: String, frame: StaticChartFrame): RenderedFrameSummary {
        val canvas = document.getElementById(canvasId) as? HTMLCanvasElement
            ?: return RenderedFrameSummary(0, 0, "Canvas '$canvasId' not found", frame.request.camera)
        val rawGl = canvas.getContext("webgl") ?: canvas.getContext("webgl2")
            ?: return RenderedFrameSummary(canvas.width, canvas.height, "WebGL is not available", frame.request.camera)
        val gl = rawGl.unsafeCast<WebGLRenderingContext>()

        gl.viewport(0, 0, canvas.width, canvas.height)
        gl.clearColor(0.82f, 0.90f, 0.96f, 1.0f)
        gl.clear(WebGLRenderingContext.COLOR_BUFFER_BIT)
        val program = createSimpleColorProgram(gl) ?: return frame.summary().copy(message = "WebGL shader setup failed")
        program.use()
        for (feature in frame.projectedFeatures) {
            when (val geometry = feature.geometry) {
                is ProjectedGeometry.Empty -> Unit
                is ProjectedGeometry.Point -> program.drawPoints(listOf(geometry.point), canvas, colorFor(feature.objectClass))
                is ProjectedGeometry.MultiPoint -> program.drawPoints(geometry.points, canvas, colorFor(feature.objectClass))
                is ProjectedGeometry.LineString -> program.drawLineStrip(geometry.points, canvas, colorFor(feature.objectClass))
                is ProjectedGeometry.Polygon -> {
                    geometry.rings.firstOrNull()?.let { ring ->
                        program.drawTriangleFan(ring, canvas, fillColorFor(feature.objectClass))
                        program.drawLineStrip(ring, canvas, colorFor(feature.objectClass))
                    }
                }
                is ProjectedGeometry.MultiPolygon -> geometry.polygons.forEach { polygon ->
                    polygon.rings.firstOrNull()?.let { ring ->
                        program.drawTriangleFan(ring, canvas, fillColorFor(feature.objectClass))
                        program.drawLineStrip(ring, canvas, colorFor(feature.objectClass))
                    }
                }
            }
        }
        if (frame.request.centerCrosshair.enabled) drawCrosshair(program, canvas, frame.request.centerCrosshair.sizePx)
        return frame.summary().copy(message = "Phase 7 static WebGL frame rendered features=${frame.featureCount}")
    }

    private fun drawCrosshair(program: BrowserSimpleColorProgram, canvas: HTMLCanvasElement, size: Double) {
        val cx = canvas.width / 2.0
        val cy = canvas.height / 2.0
        val color = floatArrayOf(0.0f, 0.0f, 0.0f, 0.85f)
        program.drawLineStrip(listOf(ScreenPoint(cx - size, cy), ScreenPoint(cx + size, cy)), canvas, color)
        program.drawLineStrip(listOf(ScreenPoint(cx, cy - size), ScreenPoint(cx, cy + size)), canvas, color)
    }

    private fun colorFor(objectClass: String): FloatArray = when (objectClass.uppercase()) {
        "DEPCNT" -> floatArrayOf(0.0f, 0.25f, 0.65f, 1.0f)
        "SOUNDG" -> floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f)
        "BOYLAT", "BCNLAT", "LIGHTS" -> floatArrayOf(0.85f, 0.1f, 0.05f, 1.0f)
        "WRECKS", "OBSTRN" -> floatArrayOf(0.45f, 0.1f, 0.1f, 1.0f)
        else -> floatArrayOf(0.05f, 0.18f, 0.24f, 1.0f)
    }

    private fun fillColorFor(objectClass: String): FloatArray = when (objectClass.uppercase()) {
        "DEPARE" -> floatArrayOf(0.70f, 0.88f, 0.96f, 0.65f)
        else -> floatArrayOf(0.78f, 0.86f, 0.80f, 0.45f)
    }
}

private class BrowserSimpleColorProgram(
    private val gl: WebGLRenderingContext,
    private val program: WebGLProgram,
    private val positionLocation: Int,
    private val colorLocation: WebGLUniformLocation?,
    private val buffer: WebGLBuffer?
) {
    fun use() {
        gl.useProgram(program)
        gl.bindBuffer(WebGLRenderingContext.ARRAY_BUFFER, buffer)
        gl.enableVertexAttribArray(positionLocation)
        gl.vertexAttribPointer(positionLocation, 2, WebGLRenderingContext.FLOAT, false, 0, 0)
        gl.enable(WebGLRenderingContext.BLEND)
        gl.blendFunc(WebGLRenderingContext.SRC_ALPHA, WebGLRenderingContext.ONE_MINUS_SRC_ALPHA)
    }

    fun drawPoints(points: List<ScreenPoint>, canvas: HTMLCanvasElement, color: FloatArray) = draw(WebGLRenderingContext.POINTS, points, canvas, color)
    fun drawLineStrip(points: List<ScreenPoint>, canvas: HTMLCanvasElement, color: FloatArray) = draw(WebGLRenderingContext.LINE_STRIP, points, canvas, color)

    fun drawTriangleFan(points: List<ScreenPoint>, canvas: HTMLCanvasElement, color: FloatArray) {
        if (points.size < 3) return
        draw(WebGLRenderingContext.TRIANGLE_FAN, points, canvas, color)
    }

    private fun draw(mode: Int, points: List<ScreenPoint>, canvas: HTMLCanvasElement, color: FloatArray) {
        if (points.isEmpty()) return
        val dataValues = Array(points.size * 2) { 0.0f }
        points.forEachIndexed { index, point ->
            dataValues[index * 2] = ((point.x / canvas.width.toDouble()) * 2.0 - 1.0).toFloat()
            dataValues[index * 2 + 1] = (1.0 - (point.y / canvas.height.toDouble()) * 2.0).toFloat()
        }
        val data = Float32Array(dataValues.size)
        data.set(dataValues)
        gl.bufferData(WebGLRenderingContext.ARRAY_BUFFER, data, WebGLRenderingContext.STREAM_DRAW)
        gl.uniform4f(colorLocation, color[0], color[1], color[2], color[3])
        gl.drawArrays(mode, 0, points.size)
    }
}

private fun createSimpleColorProgram(gl: WebGLRenderingContext): BrowserSimpleColorProgram? {
    val vertex = compileShader(gl, WebGLRenderingContext.VERTEX_SHADER, "attribute vec2 a_position; void main() { gl_Position = vec4(a_position, 0.0, 1.0); gl_PointSize = 6.0; }") ?: return null
    val fragment = compileShader(gl, WebGLRenderingContext.FRAGMENT_SHADER, "precision mediump float; uniform vec4 u_color; void main() { gl_FragColor = u_color; }") ?: return null
    val program = gl.createProgram() ?: return null
    gl.attachShader(program, vertex)
    gl.attachShader(program, fragment)
    gl.linkProgram(program)
    return BrowserSimpleColorProgram(
        gl = gl,
        program = program,
        positionLocation = gl.getAttribLocation(program, "a_position"),
        colorLocation = gl.getUniformLocation(program, "u_color"),
        buffer = gl.createBuffer()
    )
}

private fun compileShader(gl: WebGLRenderingContext, type: Int, source: String): WebGLShader? {
    val shader = gl.createShader(type) ?: return null
    gl.shaderSource(shader, source)
    gl.compileShader(shader)
    return shader
}
