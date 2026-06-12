package io.github.s57.render

import io.github.s52.core.draw.S52DrawCommand
import io.github.s52.core.geometry.Coordinate
import io.github.s52.core.geometry.EncGeometry
import io.github.s52.core.settings.MarinerSettings
import io.github.s52.preslib.PresLibPack
import io.github.s52.render.webgl.RenderStats
import io.github.s52.render.webgl.RenderViewport
import kotlinx.browser.document
import kotlinx.browser.window
import kotlin.js.console
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max

/**
 * Last-resort renderer for S-52 draw commands when the S-52 WebGL backend
 * fails before producing any draw calls.  This is not the old decoded-geometry
 * debug fallback: it consumes the already-portrayed S-52 command list, honors
 * S-52 colors where available, and keeps the chart visible instead of leaving a
 * blue canvas.
 */
internal fun renderS52CanvasCommandFallback(
    canvasId: String,
    frame: StaticChartFrame,
    portrayed: BrowserS52PortrayalResult,
    viewport: RenderViewport,
    presLib: PresLibPack,
    webglReason: String
): RenderedFrameSummary? {
    val canvas = document.getElementById(canvasId) as? HTMLCanvasElement ?: return null
    val context = try {
        canvas.getContext("2d") as? CanvasRenderingContext2D
    } catch (_: Throwable) {
        null
    } ?: return null

    val stats = S52CanvasCommandRenderer(canvas, context, presLib, portrayed.settings, viewport)
        .render(portrayed.commands)
    if (stats.drawCalls <= 0) return null
    window.asDynamic().s57S52InitialDrawCalls = stats.drawCalls
    window.asDynamic().s57S52LastResourceDrawCalls = stats.drawCalls
    window.asDynamic().s57S52ResourceRenderReady = true
    window.asDynamic().s57S52CanvasFallbackDrawCalls = stats.drawCalls

    val diagnostic = RenderPipelineDiagnostic(
        stage = RenderPipelineStage.WebGl,
        severity = RenderPipelineSeverity.Warning,
        code = "s52.webgl_backend_canvas2d_fallback",
        message = webglReason + "; rendered S-52 draw commands through Canvas2D fallback instead of decoded debug geometry",
        source = RenderPipelineSource(cellId = frame.request.cellId),
        metadata = mapOf(
            "profile" to portrayed.profile.name,
            "commands" to portrayed.commands.size.toString(),
            "drawCalls" to stats.drawCalls.toString(),
            "webgl2Shim" to webGl2KotlinJsCompatibilityShimStatus()
        )
    )
    console.warn(diagnostic.toPlainText())

    val s52 = portrayed.toSummary(drawCallCount = stats.drawCalls)
    val diagnostics = s52.diagnostics + diagnostic
    val message = "S-52 Canvas2D command fallback rendered after WebGL backend failure" +
        " profile=" + portrayed.profile +
        " encFeatures=" + portrayed.featureCount +
        " commands=" + portrayed.commands.size +
        " drawCalls=" + stats.drawCalls +
        " symbols=" + stats.symbolCount +
        " lines=" + stats.lineCount +
        " areas=" + (stats.areaFillCount + stats.areaPatternCount) +
        " text=" + (stats.textCount + stats.soundingCount)

    return frame.summary().copy(
        message = message,
        s52 = s52.copy(diagnostics = diagnostics, diagnosticCount = diagnostics.size),
        pipelineDiagnostics = diagnostics
    )
}

private class S52CanvasCommandRenderer(
    private val canvas: HTMLCanvasElement,
    private val context: CanvasRenderingContext2D,
    private val presLib: PresLibPack,
    private val settings: MarinerSettings,
    private val viewport: RenderViewport
) {
    private var areaFillCount = 0
    private var areaPatternCount = 0
    private var lineCount = 0
    private var symbolCount = 0
    private var textCount = 0
    private var soundingCount = 0
    private var drawCalls = 0

    fun render(commands: List<S52DrawCommand>): RenderStats {
        resizeToDisplaySize()
        clearBackground()
        context.asDynamic().lineCap = "round"
        context.asDynamic().lineJoin = "round"
        for (command in commands.sortedWith(compareBy<S52DrawCommand> { it.priority }.thenBy { it.viewingGroup })) {
            when (command) {
                is S52DrawCommand.AreaFill -> if (drawAreaFill(command)) areaFillCount++
                is S52DrawCommand.AreaPattern -> if (drawAreaPattern(command)) areaPatternCount++
                is S52DrawCommand.LineSimple -> if (drawLine(command.geometry, colorCss(command.colorToken, "rgba(40, 40, 40, 1.0)"), max(1.0, command.width))) lineCount++
                is S52DrawCommand.LineComplex -> if (drawLine(command.geometry, colorCss("CHBLK", "rgba(30, 30, 30, 1.0)"), 1.4)) lineCount++
                is S52DrawCommand.PointSymbol -> if (drawSymbol(command)) symbolCount++
                is S52DrawCommand.Text -> if (drawText(command.geometry, command.textExpression, command.colorToken ?: "CHBLK", 12.0)) textCount++
                is S52DrawCommand.Sounding -> if (drawText(command.geometry, command.depthLabel, command.colorToken, 11.0, monospace = true)) soundingCount++
            }
        }
        return RenderStats(
            areaFillCount = areaFillCount,
            areaPatternCount = areaPatternCount,
            lineCount = lineCount,
            symbolCount = symbolCount,
            textCount = textCount,
            soundingCount = soundingCount,
            drawCalls = drawCalls,
            batchCount = 1,
            averageCommandsPerBatch = commands.size.toDouble()
        )
    }

    private fun resizeToDisplaySize() {
        val displayWidth = canvas.clientWidth.coerceAtLeast(1)
        val displayHeight = canvas.clientHeight.coerceAtLeast(1)
        if (canvas.width != displayWidth || canvas.height != displayHeight) {
            canvas.width = displayWidth
            canvas.height = displayHeight
        }
    }

    private fun clearBackground() {
        context.fillStyle = colorCss("DEPDW", "rgba(209, 230, 245, 1.0)")
        context.fillRect(0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
    }

    private fun drawAreaFill(command: S52DrawCommand.AreaFill): Boolean {
        val drawn = fillGeometry(command.geometry, colorCss(command.colorToken, "rgba(220, 230, 235, 1.0)"))
        if (drawn) strokeGeometry(command.geometry, colorCss("CHGRD", "rgba(110, 120, 125, 0.55)"), 0.7)
        return drawn
    }

    private fun drawAreaPattern(command: S52DrawCommand.AreaPattern): Boolean {
        val fill = command.backgroundColorToken ?: when {
            command.patternName.contains("LAND", ignoreCase = true) -> "LANDA"
            command.patternName.contains("DEP", ignoreCase = true) -> "DEPIT"
            else -> "DEPVS"
        }
        val drawn = fillGeometry(command.geometry, colorCss(fill, "rgba(225, 230, 220, 0.72)"))
        if (drawn) {
            strokeGeometry(command.geometry, colorCss("CHGRD", "rgba(105, 115, 120, 0.45)"), 0.6)
        }
        return drawn
    }

    private fun drawLine(geometry: EncGeometry, color: String, width: Double): Boolean =
        strokeGeometry(geometry, color, width)

    private fun drawSymbol(command: S52DrawCommand.PointSymbol): Boolean {
        val points = anchorPoints(command.geometry)
        if (points.isEmpty()) return false
        val name = command.symbolName.uppercase()
        val color = when {
            "LIGHT" in name || "LIT" in name -> colorCss("LITYW", "rgba(220, 175, 0, 1.0)")
            "BOY" in name || "BCN" in name -> colorCss("CHBLK", "rgba(20, 20, 20, 1.0)")
            "OBSTR" in name || "UWTROC" in name || "WRECK" in name -> colorCss("DNGHL", "rgba(190, 40, 40, 1.0)")
            else -> colorCss("CHBLK", "rgba(20, 20, 20, 1.0)")
        }
        for (point in points) drawSymbolGlyph(point, name, color)
        return true
    }

    private fun drawText(
        geometry: EncGeometry,
        label: String,
        colorToken: String,
        sizePx: Double,
        monospace: Boolean = false
    ): Boolean {
        if (label.isBlank()) return false
        val points = anchorPoints(geometry)
        if (points.isEmpty()) return false
        context.fillStyle = colorCss(colorToken, "rgba(20, 20, 20, 1.0)")
        context.asDynamic().font = sizePx.toString() + "px " + (if (monospace) "monospace" else "sans-serif")
        context.asDynamic().textAlign = "center"
        context.asDynamic().textBaseline = "middle"
        for (point in points) {
            context.fillText(label, point.x + 7.0, point.y - 7.0)
            drawCalls++
        }
        return true
    }

    private fun fillGeometry(geometry: EncGeometry, color: String): Boolean = when (geometry) {
        is EncGeometry.Polygon -> fillPolygon(geometry, color)
        else -> false
    }

    private fun strokeGeometry(geometry: EncGeometry, color: String, width: Double): Boolean = when (geometry) {
        is EncGeometry.LineString -> strokePath(geometry.coordinates.map { project(it) }, color, width, close = false)
        is EncGeometry.Polygon -> {
            var drawn = strokePath(geometry.outer.map { project(it) }, color, width, close = true)
            for (hole in geometry.holes) drawn = strokePath(hole.map { project(it) }, color, width, close = true) || drawn
            drawn
        }
        else -> false
    }

    private fun fillPolygon(polygon: EncGeometry.Polygon, color: String): Boolean {
        val outer = polygon.outer.map { project(it) }
        if (outer.size < 3) return false
        context.beginPath()
        context.moveTo(outer.first().x, outer.first().y)
        outer.drop(1).forEach { context.lineTo(it.x, it.y) }
        context.closePath()
        context.fillStyle = color
        context.fill()
        drawCalls++
        return true
    }

    private fun strokePath(points: List<ScreenPoint>, color: String, width: Double, close: Boolean): Boolean {
        if (points.size < 2) return false
        context.beginPath()
        context.moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { context.lineTo(it.x, it.y) }
        if (close) context.closePath()
        context.strokeStyle = color
        context.lineWidth = width
        context.stroke()
        drawCalls++
        return true
    }

    private fun drawSymbolGlyph(point: ScreenPoint, name: String, color: String) {
        val size = when {
            "LIGHT" in name || "LIT" in name -> 10.0
            "BOY" in name || "BCN" in name -> 8.0
            else -> 7.0
        }
        context.strokeStyle = color
        context.fillStyle = color
        context.lineWidth = 1.5
        when {
            "BOY" in name -> {
                context.beginPath()
                context.moveTo(point.x, point.y - size)
                context.lineTo(point.x + size, point.y)
                context.lineTo(point.x, point.y + size)
                context.lineTo(point.x - size, point.y)
                context.closePath()
                context.stroke()
            }
            "BCN" in name -> {
                context.beginPath()
                context.moveTo(point.x, point.y - size)
                context.lineTo(point.x + size, point.y + size)
                context.lineTo(point.x - size, point.y + size)
                context.closePath()
                context.stroke()
            }
            "LIGHT" in name || "LIT" in name -> {
                strokePath(listOf(ScreenPoint(point.x - size, point.y), ScreenPoint(point.x + size, point.y)), color, 1.2, close = false)
                strokePath(listOf(ScreenPoint(point.x, point.y - size), ScreenPoint(point.x, point.y + size)), color, 1.2, close = false)
            }
            else -> {
                context.beginPath()
                context.arc(point.x, point.y, size * 0.55, 0.0, PI * 2.0)
                context.stroke()
            }
        }
        drawCalls++
    }

    private fun anchorPoints(geometry: EncGeometry): List<ScreenPoint> = when (geometry) {
        is EncGeometry.Point -> listOf(project(geometry.coordinate))
        is EncGeometry.MultiPoint -> geometry.coordinates.map { project(it) }
        is EncGeometry.LineString -> listOfNotNull(geometry.coordinates.middleCoordinate()?.let { project(it) })
        is EncGeometry.Polygon -> listOfNotNull(geometry.outer.centroidCoordinate()?.let { project(it) })
    }

    private fun project(coordinate: Coordinate): ScreenPoint {
        val lonSpan = (viewport.east - viewport.west).takeIf { abs(it) > 1e-12 } ?: 1.0
        val latSpan = (viewport.north - viewport.south).takeIf { abs(it) > 1e-12 } ?: 1.0
        val x = ((coordinate.lon - viewport.west) / lonSpan) * canvas.width.toDouble()
        val y = ((viewport.north - coordinate.lat) / latSpan) * canvas.height.toDouble()
        return ScreenPoint(x, y)
    }

    private fun colorCss(token: String?, fallback: String): String {
        if (token.isNullOrBlank()) return fallback
        val color = presLib.colors.color(settings.palette, token) ?: return fallback
        return "rgba(" + color.r + ", " + color.g + ", " + color.b + ", 1.0)"
    }
}

private fun List<Coordinate>.middleCoordinate(): Coordinate? = when {
    isEmpty() -> null
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
