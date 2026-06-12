package io.github.s57.render

import io.github.s52.core.draw.S52DrawCommand
import io.github.s52.core.geometry.Coordinate
import io.github.s52.core.geometry.EncGeometry
import io.github.s52.core.settings.MarinerSettings
import io.github.s52.preslib.PresLibPack
import io.github.s52.render.webgl.RenderViewport
import org.khronos.webgl.Float32Array
import org.khronos.webgl.WebGLBuffer
import org.khronos.webgl.WebGLProgram
import org.khronos.webgl.WebGLRenderingContext
import org.khronos.webgl.WebGLShader
import org.khronos.webgl.WebGLTexture
import org.khronos.webgl.WebGLUniformLocation
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import kotlin.js.unsafeCast
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * WebGL text/sounding pass used by the strict S-52 browser renderer.
 *
 * This is not a Canvas2D chart fallback: labels are rasterized into a small
 * per-frame font atlas and composited as WebGL texture quads on the same chart
 * canvas after the S-52 WebGL geometry pass.  Text draw commands are removed
 * from the upstream command list before rendering so chart labels have exactly
 * one execution path.
 */
internal class BrowserS52WebGlTextPostpass(
    private val canvas: HTMLCanvasElement,
    private val presLib: PresLibPack
) {
    private val gl: WebGLRenderingContext = canvas.getContext("webgl2").unsafeCast<WebGLRenderingContext>()
    private val program: BrowserS52TextProgram = BrowserS52TextProgram(gl)
    private val atlasCanvas: HTMLCanvasElement = canvas.ownerDocument!!.createElement("canvas").unsafeCast<HTMLCanvasElement>()
    private val atlasContext: CanvasRenderingContext2D = atlasCanvas.getContext("2d").unsafeCast<CanvasRenderingContext2D>()
    private var atlasTexture: WebGLTexture? = null

    fun render(
        commands: List<S52DrawCommand>,
        settings: MarinerSettings,
        viewport: RenderViewport
    ): BrowserS52TextPostpassStats {
        val textCommands = commands.filter { it is S52DrawCommand.Text || it is S52DrawCommand.Sounding }
        if (textCommands.isEmpty()) return BrowserS52TextPostpassStats()

        val placements = buildPlacements(textCommands, viewport, settings)
        if (placements.isEmpty()) return BrowserS52TextPostpassStats()

        val atlas = paintAtlas(placements)
        val texture = uploadAtlas(atlas)
        program.use()
        gl.activeTexture(WebGLRenderingContext.TEXTURE0)
        gl.bindTexture(WebGLRenderingContext.TEXTURE_2D, texture)
        program.setTextureUnit(0)
        gl.enable(WebGLRenderingContext.BLEND)
        gl.blendFunc(WebGLRenderingContext.SRC_ALPHA, WebGLRenderingContext.ONE_MINUS_SRC_ALPHA)

        var drawCalls = 0
        for (placement in placements) {
            val quad = placement.toQuad(canvas.width, canvas.height, atlas.width, atlas.height)
            drawCalls += program.draw(quad)
        }
        return BrowserS52TextPostpassStats(
            textCount = placements.count { it.kind == BrowserS52TextKind.Text },
            soundingCount = placements.count { it.kind == BrowserS52TextKind.Sounding },
            drawCalls = drawCalls
        )
    }

    private fun buildPlacements(
        commands: List<S52DrawCommand>,
        viewport: RenderViewport,
        settings: MarinerSettings
    ): List<BrowserS52TextPlacement> {
        val occupied = ArrayList<BrowserS52PixelBounds>()
        val placements = ArrayList<BrowserS52TextPlacement>()
        for (command in commands) {
            val anchor = anchor(command.geometry, viewport) ?: continue
            val label = when (command) {
                is S52DrawCommand.Text -> command.textExpression.ifBlank { command.rawArgs.firstOrNull().orEmpty() }.trim()
                is S52DrawCommand.Sounding -> command.depthLabel.trim()
                else -> ""
            }
            if (label.isBlank()) continue
            val kind = if (command is S52DrawCommand.Sounding) BrowserS52TextKind.Sounding else BrowserS52TextKind.Text
            val style = textStyle(command, kind, settings)
            val metrics = measure(label, style)
            val bounds = BrowserS52PixelBounds(
                minX = anchor.x - metrics.width * 0.5 - style.paddingPx,
                minY = anchor.y - metrics.height * 0.5 - style.paddingPx,
                maxX = anchor.x + metrics.width * 0.5 + style.paddingPx,
                maxY = anchor.y + metrics.height * 0.5 + style.paddingPx
            )
            if (bounds.outside(canvas.width.toDouble(), canvas.height.toDouble())) continue
            if (occupied.any { it.intersects(bounds) }) continue
            occupied += bounds
            placements += BrowserS52TextPlacement(label, kind, style, metrics, anchor, bounds)
        }
        return placements
    }

    private fun textStyle(
        command: S52DrawCommand,
        kind: BrowserS52TextKind,
        settings: MarinerSettings
    ): BrowserS52TextStyle {
        val token = when (command) {
            is S52DrawCommand.Text -> command.colorToken ?: "CHBLK"
            is S52DrawCommand.Sounding -> command.colorToken
            else -> "CHBLK"
        }
        val foreground = presLib.colors.color(settings.palette, token) ?: presLib.colors.color(settings.palette, "CHBLK")
        val fill = foreground?.let { "rgba(${it.r},${it.g},${it.b},1.0)" } ?: "rgba(20,32,42,1.0)"
        val luminance = foreground?.let { 0.2126 * it.r + 0.7152 * it.g + 0.0722 * it.b } ?: 0.0
        val halo = if (luminance < 128.0) "rgba(255,255,246,0.88)" else "rgba(32,32,32,0.58)"
        return if (kind == BrowserS52TextKind.Sounding) {
            BrowserS52TextStyle(
                fontPx = 13,
                cssFont = "600 13px \"Aptos\", \"Segoe UI\", \"Helvetica Neue\", Arial, sans-serif",
                fillStyle = fill,
                haloStyle = halo,
                paddingPx = 3.0
            )
        } else {
            BrowserS52TextStyle(
                fontPx = 12,
                cssFont = "500 12px \"Aptos\", \"Segoe UI\", \"Helvetica Neue\", Arial, sans-serif",
                fillStyle = fill,
                haloStyle = halo,
                paddingPx = 4.0
            )
        }
    }

    private fun measure(label: String, style: BrowserS52TextStyle): BrowserS52TextMetrics {
        atlasContext.font = style.cssFont
        val width = ceil(atlasContext.measureText(label).width + style.paddingPx * 2.0).coerceAtLeast(1.0)
        val height = ceil(style.fontPx * 1.55 + style.paddingPx * 2.0).coerceAtLeast(1.0)
        return BrowserS52TextMetrics(width, height)
    }

    private fun paintAtlas(placements: List<BrowserS52TextPlacement>): BrowserS52AtlasSize {
        val maxWidth = placements.maxOf { ceil(it.metrics.width).toInt() }.coerceAtLeast(1)
        val rowHeight = placements.maxOf { ceil(it.metrics.height).toInt() }.coerceAtLeast(1)
        val columns = max(1, min(8, 1024 / maxWidth.coerceAtLeast(1)))
        val rows = ceil(placements.size.toDouble() / columns.toDouble()).toInt().coerceAtLeast(1)
        val width = nextPowerOfTwo((columns * maxWidth).coerceAtLeast(1))
        val height = nextPowerOfTwo((rows * rowHeight).coerceAtLeast(1))
        atlasCanvas.width = width
        atlasCanvas.height = height
        atlasContext.clearRect(0.0, 0.0, width.toDouble(), height.toDouble())
        atlasContext.asDynamic().textAlign = "center"
        atlasContext.asDynamic().textBaseline = "middle"

        for ((index, placement) in placements.withIndex()) {
            val col = index % columns
            val row = index / columns
            val x = col * maxWidth
            val y = row * rowHeight
            placement.atlasX = x
            placement.atlasY = y
            placement.atlasW = ceil(placement.metrics.width).toInt()
            placement.atlasH = ceil(placement.metrics.height).toInt()
            atlasContext.font = placement.style.cssFont
            atlasContext.asDynamic().lineJoin = "round"
            atlasContext.lineWidth = 4.0
            atlasContext.strokeStyle = placement.style.haloStyle
            atlasContext.fillStyle = placement.style.fillStyle
            val cx = x + placement.atlasW * 0.5
            val cy = y + placement.atlasH * 0.5
            atlasContext.strokeText(placement.label, cx, cy)
            atlasContext.fillText(placement.label, cx, cy)
        }
        return BrowserS52AtlasSize(width, height)
    }

    private fun uploadAtlas(size: BrowserS52AtlasSize): WebGLTexture {
        val texture = atlasTexture ?: gl.createTexture().also { atlasTexture = it }!!
        gl.bindTexture(WebGLRenderingContext.TEXTURE_2D, texture)
        gl.texParameteri(WebGLRenderingContext.TEXTURE_2D, WebGLRenderingContext.TEXTURE_MIN_FILTER, WebGLRenderingContext.LINEAR)
        gl.texParameteri(WebGLRenderingContext.TEXTURE_2D, WebGLRenderingContext.TEXTURE_MAG_FILTER, WebGLRenderingContext.LINEAR)
        gl.texParameteri(WebGLRenderingContext.TEXTURE_2D, WebGLRenderingContext.TEXTURE_WRAP_S, WebGLRenderingContext.CLAMP_TO_EDGE)
        gl.texParameteri(WebGLRenderingContext.TEXTURE_2D, WebGLRenderingContext.TEXTURE_WRAP_T, WebGLRenderingContext.CLAMP_TO_EDGE)
        gl.asDynamic().texImage2D(
            WebGLRenderingContext.TEXTURE_2D,
            0,
            WebGLRenderingContext.RGBA,
            WebGLRenderingContext.RGBA,
            WebGLRenderingContext.UNSIGNED_BYTE,
            atlasCanvas
        )
        return texture
    }

    private fun anchor(geometry: EncGeometry, viewport: RenderViewport): BrowserS52PixelPoint? = when (geometry) {
        is EncGeometry.Point -> project(geometry.coordinate, viewport)
        is EncGeometry.MultiPoint -> geometry.coordinates.firstOrNull()?.let { project(it, viewport) }
        is EncGeometry.LineString -> lineAnchor(geometry.coordinates, viewport)
        is EncGeometry.Polygon -> polygonAnchor(geometry.outer, viewport)
    }

    private fun lineAnchor(coordinates: List<Coordinate>, viewport: RenderViewport): BrowserS52PixelPoint? {
        if (coordinates.isEmpty()) return null
        val points = coordinates.map { project(it, viewport) }
        var total = 0.0
        for (i in 0 until points.lastIndex) total += distance(points[i], points[i + 1])
        if (total <= 1e-9) return points[points.size / 2]
        val half = total * 0.5
        var acc = 0.0
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            val segment = distance(a, b)
            if (acc + segment >= half && segment > 1e-9) {
                val t = ((half - acc) / segment).coerceIn(0.0, 1.0)
                return BrowserS52PixelPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
            acc += segment
        }
        return points.last()
    }

    private fun polygonAnchor(coordinates: List<Coordinate>, viewport: RenderViewport): BrowserS52PixelPoint? {
        if (coordinates.isEmpty()) return null
        val points = coordinates.map { project(it, viewport) }
        var twiceArea = 0.0
        var cx = 0.0
        var cy = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            val cross = a.x * b.y - b.x * a.y
            twiceArea += cross
            cx += (a.x + b.x) * cross
            cy += (a.y + b.y) * cross
        }
        if (abs(twiceArea) > 1e-9) {
            val factor = 1.0 / (3.0 * twiceArea)
            return BrowserS52PixelPoint(cx * factor, cy * factor)
        }
        return BrowserS52PixelPoint(
            x = (points.minOf { it.x } + points.maxOf { it.x }) * 0.5,
            y = (points.minOf { it.y } + points.maxOf { it.y }) * 0.5
        )
    }

    private fun project(coordinate: Coordinate, viewport: RenderViewport): BrowserS52PixelPoint {
        val x = ((coordinate.lon - viewport.west) / (viewport.east - viewport.west).coerceAtLeast(1e-12)) * canvas.width.toDouble()
        val y = (1.0 - ((coordinate.lat - viewport.south) / (viewport.north - viewport.south).coerceAtLeast(1e-12))) * canvas.height.toDouble()
        return BrowserS52PixelPoint(x, y)
    }

    private fun BrowserS52TextPlacement.toQuad(canvasWidth: Int, canvasHeight: Int, atlasWidth: Int, atlasHeight: Int): Float32Array {
        val x0 = anchor.x - atlasW * 0.5
        val y0 = anchor.y - atlasH * 0.5
        val x1 = x0 + atlasW
        val y1 = y0 + atlasH
        val cx0 = (x0 / canvasWidth.toDouble()) * 2.0 - 1.0
        val cx1 = (x1 / canvasWidth.toDouble()) * 2.0 - 1.0
        val cy0 = 1.0 - (y0 / canvasHeight.toDouble()) * 2.0
        val cy1 = 1.0 - (y1 / canvasHeight.toDouble()) * 2.0
        val u0 = atlasX.toDouble() / atlasWidth.toDouble()
        val v0 = atlasY.toDouble() / atlasHeight.toDouble()
        val u1 = (atlasX + atlasW).toDouble() / atlasWidth.toDouble()
        val v1 = (atlasY + atlasH).toDouble() / atlasHeight.toDouble()
        return Float32Array(arrayOf(
            cx0.toFloat(), cy0.toFloat(), u0.toFloat(), v0.toFloat(),
            cx1.toFloat(), cy0.toFloat(), u1.toFloat(), v0.toFloat(),
            cx1.toFloat(), cy1.toFloat(), u1.toFloat(), v1.toFloat(),
            cx0.toFloat(), cy0.toFloat(), u0.toFloat(), v0.toFloat(),
            cx1.toFloat(), cy1.toFloat(), u1.toFloat(), v1.toFloat(),
            cx0.toFloat(), cy1.toFloat(), u0.toFloat(), v1.toFloat()
        ))
    }

    private fun distance(a: BrowserS52PixelPoint, b: BrowserS52PixelPoint): Double = hypot(b.x - a.x, b.y - a.y)

    private fun nextPowerOfTwo(value: Int): Int {
        var result = 1
        while (result < value) result = result shl 1
        return result
    }
}

internal data class BrowserS52TextPostpassStats(
    val textCount: Int = 0,
    val soundingCount: Int = 0,
    val drawCalls: Int = 0
)

private enum class BrowserS52TextKind { Text, Sounding }
private data class BrowserS52PixelPoint(val x: Double, val y: Double)
private data class BrowserS52TextMetrics(val width: Double, val height: Double)
private data class BrowserS52AtlasSize(val width: Int, val height: Int)
private data class BrowserS52TextStyle(
    val fontPx: Int,
    val cssFont: String,
    val fillStyle: String,
    val haloStyle: String,
    val paddingPx: Double
)
private data class BrowserS52PixelBounds(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    fun outside(width: Double, height: Double): Boolean = maxX < 0.0 || minX > width || maxY < 0.0 || minY > height
    fun intersects(other: BrowserS52PixelBounds): Boolean = minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY
}
private data class BrowserS52TextPlacement(
    val label: String,
    val kind: BrowserS52TextKind,
    val style: BrowserS52TextStyle,
    val metrics: BrowserS52TextMetrics,
    val anchor: BrowserS52PixelPoint,
    val bounds: BrowserS52PixelBounds,
    var atlasX: Int = 0,
    var atlasY: Int = 0,
    var atlasW: Int = 0,
    var atlasH: Int = 0
)

private class BrowserS52TextProgram(private val gl: WebGLRenderingContext) {
    private val program: WebGLProgram = linkProgram()
    private val vertexBuffer: WebGLBuffer = gl.createBuffer() ?: error("Unable to create S-52 text vertex buffer")
    private val aPosition: Int = gl.getAttribLocation(program, "a_position")
    private val aTexCoord: Int = gl.getAttribLocation(program, "a_texCoord")
    private val uTexture: WebGLUniformLocation? = gl.getUniformLocation(program, "u_texture")

    fun use() {
        gl.useProgram(program)
        gl.bindBuffer(WebGLRenderingContext.ARRAY_BUFFER, vertexBuffer)
        gl.enableVertexAttribArray(aPosition)
        gl.enableVertexAttribArray(aTexCoord)
        gl.vertexAttribPointer(aPosition, 2, WebGLRenderingContext.FLOAT, false, 16, 0)
        gl.vertexAttribPointer(aTexCoord, 2, WebGLRenderingContext.FLOAT, false, 16, 8)
    }

    fun setTextureUnit(unit: Int) {
        if (uTexture != null) gl.uniform1i(uTexture, unit)
    }

    fun draw(vertices: Float32Array): Int {
        gl.bindBuffer(WebGLRenderingContext.ARRAY_BUFFER, vertexBuffer)
        gl.bufferData(WebGLRenderingContext.ARRAY_BUFFER, vertices, WebGLRenderingContext.STREAM_DRAW)
        gl.drawArrays(WebGLRenderingContext.TRIANGLES, 0, 6)
        return 1
    }

    private fun linkProgram(): WebGLProgram {
        val vertexShader = compile(WebGLRenderingContext.VERTEX_SHADER, """
            attribute vec2 a_position;
            attribute vec2 a_texCoord;
            varying vec2 v_texCoord;
            void main() {
                gl_Position = vec4(a_position, 0.0, 1.0);
                v_texCoord = a_texCoord;
            }
        """.trimIndent())
        val fragmentShader = compile(WebGLRenderingContext.FRAGMENT_SHADER, """
            precision mediump float;
            uniform sampler2D u_texture;
            varying vec2 v_texCoord;
            void main() {
                gl_FragColor = texture2D(u_texture, v_texCoord);
            }
        """.trimIndent())
        val linked = gl.createProgram() ?: error("Unable to create S-52 text shader program")
        gl.attachShader(linked, vertexShader)
        gl.attachShader(linked, fragmentShader)
        gl.linkProgram(linked)
        if (gl.getProgramParameter(linked, WebGLRenderingContext.LINK_STATUS) != true) {
            error("Unable to link S-52 text shader program: " + gl.getProgramInfoLog(linked))
        }
        return linked
    }

    private fun compile(type: Int, source: String): WebGLShader {
        val shader = gl.createShader(type) ?: error("Unable to create S-52 text shader")
        gl.shaderSource(shader, source)
        gl.compileShader(shader)
        if (gl.getShaderParameter(shader, WebGLRenderingContext.COMPILE_STATUS) != true) {
            error("Unable to compile S-52 text shader: " + gl.getShaderInfoLog(shader))
        }
        return shader
    }
}
