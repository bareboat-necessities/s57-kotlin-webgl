package io.github.s57.render

import io.github.s52.core.draw.S52DrawCommand
import io.github.s52.core.geometry.Coordinate
import io.github.s52.core.geometry.EncGeometry
import io.github.s52.core.settings.MarinerSettings
import io.github.s52.core.settings.S52Palette
import io.github.s52.preslib.PresLibPack
import io.github.s52.render.webgl.RenderViewport
import kotlin.js.unsafeCast
import kotlin.math.abs
import kotlin.math.max
import org.khronos.webgl.Float32Array
import org.khronos.webgl.WebGLBuffer
import org.khronos.webgl.WebGLProgram
import org.khronos.webgl.WebGLRenderingContext
import org.khronos.webgl.WebGLShader
import org.khronos.webgl.WebGLUniformLocation
import org.w3c.dom.HTMLCanvasElement

/**
 * Browser-only WebGL label overlay used by the demo.
 *
 * The S-52 WebGL backend remains responsible for fills, lines, symbols, and
 * other chart primitives.  This overlay deliberately replaces browser/native
 * text rendering for labels with a small vector stroke font drawn with WebGL
 * line batches.  It avoids Canvas2D text, browser font differences, and the
 * blurry/pixelated Chromium text path while keeping all chart drawing on the
 * WebGL canvas.
 */
internal class BrowserS52WebGlVectorLabelOverlay(
    private val canvas: HTMLCanvasElement,
    private val presLib: PresLibPack
) {
    private val gl: WebGLRenderingContext = (canvas.getContext("webgl2") ?: error("WebGL2 is not available for S-52 vector labels"))
        .unsafeCast<WebGLRenderingContext>()
    private val program: VectorLabelProgram = createVectorLabelProgram(gl)
        ?: error("S-52 vector-label WebGL shader setup failed")

    fun render(
        commands: List<S52DrawCommand>,
        settings: MarinerSettings,
        viewport: RenderViewport
    ): BrowserS52VectorLabelStats {
        if (commands.isEmpty()) return BrowserS52VectorLabelStats()
        gl.viewport(0, 0, canvas.width, canvas.height)
        gl.disable(WebGLRenderingContext.DEPTH_TEST)
        gl.enable(WebGLRenderingContext.BLEND)
        gl.blendFunc(WebGLRenderingContext.SRC_ALPHA, WebGLRenderingContext.ONE_MINUS_SRC_ALPHA)
        program.use()

        val grouped = linkedMapOf<String, MutableList<LabelStroke>>()
        var textCount = 0
        var soundingCount = 0
        for (command in commands.sortedWith(compareBy<S52DrawCommand> { it.priority }.thenBy { it.viewingGroup })) {
            val label = command.vectorLabelText()
            if (label.isBlank()) continue
            val anchor = command.geometry.vectorLabelAnchor() ?: continue
            val point = projectLabel(anchor, viewport, canvas.width, canvas.height)
            if (point.x < -80.0 || point.y < -40.0 || point.x > canvas.width + 80.0 || point.y > canvas.height + 40.0) continue
            val isSounding = command is S52DrawCommand.Sounding
            val token = command.vectorLabelColorToken()
            val strokes = buildLabelStrokes(
                text = label,
                origin = point,
                sounding = isSounding
            )
            if (strokes.isEmpty()) continue
            grouped.getOrPut(token) { mutableListOf() }.addAll(strokes)
            if (isSounding) soundingCount++ else textCount++
        }

        var drawCalls = 0
        for ((token, strokes) in grouped) {
            if (strokes.isEmpty()) continue
            program.drawStrokes(
                strokes = strokes,
                canvas = canvas,
                color = colorFor(settings.palette, token)
            )
            drawCalls++
        }
        return BrowserS52VectorLabelStats(textCount = textCount, soundingCount = soundingCount, drawCalls = drawCalls)
    }

    private fun colorFor(palette: S52Palette, token: String): FloatArray {
        val color = presLib.colors.color(palette, token) ?: presLib.colors.color(palette, "CHBLK")
        return if (color != null) {
            floatArrayOf(color.r / 255.0f, color.g / 255.0f, color.b / 255.0f, 0.92f)
        } else {
            floatArrayOf(0.05f, 0.07f, 0.09f, 0.92f)
        }
    }
}

internal data class BrowserS52VectorLabelStats(
    val textCount: Int = 0,
    val soundingCount: Int = 0,
    val drawCalls: Int = 0
)

private data class LabelStroke(val x0: Double, val y0: Double, val x1: Double, val y1: Double)

private fun S52DrawCommand.vectorLabelText(): String = when (this) {
    is S52DrawCommand.Text -> textExpression.ifBlank { rawArgs.firstOrNull()?.toString().orEmpty() }
    is S52DrawCommand.Sounding -> depthLabel
    else -> ""
}.trim().replace(Regex("\\s+"), " ").take(if (this is S52DrawCommand.Sounding) 8 else 34)

private fun S52DrawCommand.vectorLabelColorToken(): String = when (this) {
    is S52DrawCommand.Text -> colorToken ?: "CHBLK"
    is S52DrawCommand.Sounding -> (colorToken ?: "CHBLK").ifBlank { "CHBLK" }
    else -> "CHBLK"
}

private val S52DrawCommand.geometry: EncGeometry
    get() = when (this) {
        is S52DrawCommand.AreaFill -> geometry
        is S52DrawCommand.AreaPattern -> geometry
        is S52DrawCommand.LineSimple -> geometry
        is S52DrawCommand.LineComplex -> geometry
        is S52DrawCommand.PointSymbol -> geometry
        is S52DrawCommand.Text -> geometry
        is S52DrawCommand.Sounding -> geometry
    }

private fun EncGeometry.vectorLabelAnchor(): Coordinate? = when (this) {
    is EncGeometry.Point -> coordinate
    is EncGeometry.MultiPoint -> coordinates.firstOrNull()
    is EncGeometry.LineString -> coordinates.middleCoordinate()
    is EncGeometry.Polygon -> outer.centroidCoordinate()
}

private fun List<Coordinate>.middleCoordinate(): Coordinate? = when {
    isEmpty() -> null
    size == 1 -> first()
    else -> this[size / 2]
}

private fun List<Coordinate>.centroidCoordinate(): Coordinate? {
    if (isEmpty()) return null
    var lon = 0.0
    var lat = 0.0
    for (coordinate in this) {
        lon += coordinate.lon
        lat += coordinate.lat
    }
    return Coordinate(lon / size.toDouble(), lat / size.toDouble())
}

private fun projectLabel(coordinate: Coordinate, viewport: RenderViewport, widthPx: Int, heightPx: Int): ScreenPoint {
    val lonSpan = (viewport.east - viewport.west).takeIf { abs(it) > 1e-12 } ?: 1.0
    val latSpan = (viewport.north - viewport.south).takeIf { abs(it) > 1e-12 } ?: 1.0
    val x = ((coordinate.lon - viewport.west) / lonSpan) * widthPx.toDouble()
    val y = ((viewport.north - coordinate.lat) / latSpan) * heightPx.toDouble()
    return ScreenPoint(x, y)
}

private fun buildLabelStrokes(text: String, origin: ScreenPoint, sounding: Boolean): List<LabelStroke> {
    val normalized = text.uppercase().filter { it in supportedVectorChars }
    if (normalized.isBlank()) return emptyList()
    val cell = if (sounding) 1.55 else 1.35
    val charAdvance = cell * 6.15
    val width = (normalized.length - 1).coerceAtLeast(0) * charAdvance + cell * 5.0
    var x = origin.x - width * 0.5
    val y = origin.y - cell * 3.5
    val strokes = ArrayList<LabelStroke>(normalized.length * 10)
    for (char in normalized) {
        val glyph = vectorGlyphs[char]
        if (glyph != null) appendGlyphStrokes(strokes, glyph, x, y, cell)
        x += charAdvance
    }
    return strokes
}

private val supportedVectorChars: Set<Char> = (
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-/#()' "
).toSet()

private fun appendGlyphStrokes(out: MutableList<LabelStroke>, glyph: List<String>, x: Double, y: Double, cell: Double) {
    for (rowIndex in glyph.indices) {
        val row = glyph[rowIndex]
        var start = -1
        for (col in 0..row.length) {
            val on = col < row.length && row[col] == '1'
            if (on && start < 0) start = col
            if ((!on || col == row.length) && start >= 0) {
                val end = col - 1
                out += LabelStroke(
                    x0 = x + start * cell,
                    y0 = y + rowIndex * cell,
                    x1 = x + (end + 1) * cell,
                    y1 = y + rowIndex * cell
                )
                start = -1
            }
        }
    }
}

private class VectorLabelProgram(
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
    }

    fun drawStrokes(strokes: List<LabelStroke>, canvas: HTMLCanvasElement, color: FloatArray) {
        if (strokes.isEmpty()) return
        val values = Array(strokes.size * 4) { 0.0f }
        var i = 0
        for (stroke in strokes) {
            values[i++] = ((stroke.x0 / canvas.width.toDouble()) * 2.0 - 1.0).toFloat()
            values[i++] = (1.0 - (stroke.y0 / canvas.height.toDouble()) * 2.0).toFloat()
            values[i++] = ((stroke.x1 / canvas.width.toDouble()) * 2.0 - 1.0).toFloat()
            values[i++] = (1.0 - (stroke.y1 / canvas.height.toDouble()) * 2.0).toFloat()
        }
        val data = Float32Array(values.size)
        data.set(values)
        gl.bufferData(WebGLRenderingContext.ARRAY_BUFFER, data, WebGLRenderingContext.STREAM_DRAW)
        gl.uniform4f(colorLocation, color[0], color[1], color[2], color[3])
        gl.lineWidth(1.0f)
        gl.drawArrays(WebGLRenderingContext.LINES, 0, strokes.size * 2)
    }
}

private fun createVectorLabelProgram(gl: WebGLRenderingContext): VectorLabelProgram? {
    val vertex = compileVectorLabelShader(gl, WebGLRenderingContext.VERTEX_SHADER, "attribute vec2 a_position; void main() { gl_Position = vec4(a_position, 0.0, 1.0); }") ?: return null
    val fragment = compileVectorLabelShader(gl, WebGLRenderingContext.FRAGMENT_SHADER, "precision mediump float; uniform vec4 u_color; void main() { gl_FragColor = u_color; }") ?: return null
    val program = gl.createProgram() ?: return null
    gl.attachShader(program, vertex)
    gl.attachShader(program, fragment)
    gl.linkProgram(program)
    return VectorLabelProgram(
        gl = gl,
        program = program,
        positionLocation = gl.getAttribLocation(program, "a_position"),
        colorLocation = gl.getUniformLocation(program, "u_color"),
        buffer = gl.createBuffer()
    )
}

private fun compileVectorLabelShader(gl: WebGLRenderingContext, type: Int, source: String): WebGLShader? {
    val shader = gl.createShader(type) ?: return null
    gl.shaderSource(shader, source)
    gl.compileShader(shader)
    return shader
}

private val vectorGlyphs: Map<Char, List<String>> = mapOf(
    'A' to listOf("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
    'B' to listOf("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
    'C' to listOf("01111", "10000", "10000", "10000", "10000", "10000", "01111"),
    'D' to listOf("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
    'E' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
    'F' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
    'G' to listOf("01111", "10000", "10000", "10111", "10001", "10001", "01111"),
    'H' to listOf("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
    'I' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "11111"),
    'J' to listOf("00111", "00010", "00010", "00010", "00010", "10010", "01100"),
    'K' to listOf("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
    'L' to listOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
    'M' to listOf("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
    'N' to listOf("10001", "11001", "10101", "10011", "10001", "10001", "10001"),
    'O' to listOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
    'P' to listOf("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
    'Q' to listOf("01110", "10001", "10001", "10001", "10101", "10010", "01101"),
    'R' to listOf("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
    'S' to listOf("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
    'T' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
    'U' to listOf("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
    'V' to listOf("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
    'W' to listOf("10001", "10001", "10001", "10101", "10101", "10101", "01010"),
    'X' to listOf("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
    'Y' to listOf("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
    'Z' to listOf("11111", "00001", "00010", "00100", "01000", "10000", "11111"),
    '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
    '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
    '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
    '3' to listOf("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
    '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
    '5' to listOf("11111", "10000", "10000", "11110", "00001", "00001", "11110"),
    '6' to listOf("01110", "10000", "10000", "11110", "10001", "10001", "01110"),
    '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
    '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
    '9' to listOf("01110", "10001", "10001", "01111", "00001", "00001", "01110"),
    '.' to listOf("00000", "00000", "00000", "00000", "00000", "01100", "01100"),
    '-' to listOf("00000", "00000", "00000", "11111", "00000", "00000", "00000"),
    '/' to listOf("00001", "00001", "00010", "00100", "01000", "10000", "10000"),
    '#' to listOf("01010", "01010", "11111", "01010", "11111", "01010", "01010"),
    '(' to listOf("00110", "01000", "10000", "10000", "10000", "01000", "00110"),
    ')' to listOf("01100", "00010", "00001", "00001", "00001", "00010", "01100"),
    '\'' to listOf("00100", "00100", "01000", "00000", "00000", "00000", "00000"),
    ' ' to listOf("00000", "00000", "00000", "00000", "00000", "00000", "00000")
)
