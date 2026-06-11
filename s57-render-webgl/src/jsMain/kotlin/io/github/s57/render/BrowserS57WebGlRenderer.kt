package io.github.s57.render

import kotlinx.browser.document
import org.khronos.webgl.Float32Array
import org.khronos.webgl.WebGLBuffer
import org.khronos.webgl.WebGLProgram
import org.khronos.webgl.WebGLRenderingContext
import org.khronos.webgl.WebGLShader
import org.khronos.webgl.WebGLUniformLocation
import org.w3c.dom.CanvasRenderingContext2D
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
            ?: return renderFrame2d(
                canvas = canvas,
                frame = frame,
                clearCanvas = true,
                messagePrefix = "Canvas2D fallback frame rendered because WebGL is not available"
            )
        val gl = rawGl.unsafeCast<WebGLRenderingContext>()

        gl.viewport(0, 0, canvas.width, canvas.height)
        gl.clearColor(0.82f, 0.90f, 0.96f, 1.0f)
        gl.clear(WebGLRenderingContext.COLOR_BUFFER_BIT)
        val program = createSimpleColorProgram(gl) ?: return frame.summary().copy(message = "WebGL shader setup failed")
        program.use()
        val counts = drawDecodedGeometry(program, canvas, frame, includePointGlyphs = true, includeSoundingPointGlyphs = true)
        if (frame.request.centerCrosshair.enabled) drawCrosshair(program, canvas, frame.request.centerCrosshair.sizePx)
        return frame.summary().copy(
            message = "Phase 7 static WebGL frame rendered features=${frame.featureCount} " + counts.toMessage()
        )
    }

    fun renderGeometryOverlay(
        canvasId: String,
        frame: StaticChartFrame,
        includePointGlyphs: Boolean = true,
        includeSoundingPointGlyphs: Boolean = false
    ): RenderedFrameSummary {
        val canvas = document.getElementById(canvasId) as? HTMLCanvasElement
            ?: return RenderedFrameSummary(0, 0, "Canvas '$canvasId' not found", frame.request.camera)
        val rawGl = canvas.getContext("webgl") ?: canvas.getContext("webgl2")
            ?: return renderFrame2d(
                canvas = canvas,
                frame = frame,
                clearCanvas = false,
                includePointGlyphs = includePointGlyphs,
                includeSoundingPointGlyphs = includeSoundingPointGlyphs,
                messagePrefix = "Canvas2D decoded geometry overlay rendered because WebGL is not available"
            )
        val gl = rawGl.unsafeCast<WebGLRenderingContext>()

        gl.viewport(0, 0, canvas.width, canvas.height)
        val program = createSimpleColorProgram(gl) ?: return frame.summary().copy(message = "WebGL geometry overlay shader setup failed")
        program.use()
        val counts = drawDecodedGeometry(program, canvas, frame, includePointGlyphs, includeSoundingPointGlyphs)
        return frame.summary().copy(message = "decoded geometry overlay rendered " + counts.toMessage())
    }

    private fun renderFrame2d(
        canvas: HTMLCanvasElement,
        frame: StaticChartFrame,
        clearCanvas: Boolean,
        includePointGlyphs: Boolean = true,
        includeSoundingPointGlyphs: Boolean = true,
        messagePrefix: String
    ): RenderedFrameSummary {
        val context = canvas.getContext("2d") as? CanvasRenderingContext2D
            ?: return RenderedFrameSummary(canvas.width, canvas.height, "WebGL and Canvas2D are not available", frame.request.camera)
        if (clearCanvas) {
            context.fillStyle = "rgba(209, 230, 245, 1.0)"
            context.fillRect(0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
        }
        context.asDynamic().lineCap = "round"
        context.asDynamic().lineJoin = "round"
        val counts = drawDecodedGeometry2d(context, frame, includePointGlyphs, includeSoundingPointGlyphs)
        if (frame.request.centerCrosshair.enabled) drawCrosshair2d(context, canvas, frame.request.centerCrosshair.sizePx)
        return frame.summary().copy(message = "$messagePrefix features=${frame.featureCount} " + counts.toMessage())
    }

    private fun drawDecodedGeometry2d(
        context: CanvasRenderingContext2D,
        frame: StaticChartFrame,
        includePointGlyphs: Boolean,
        includeSoundingPointGlyphs: Boolean
    ): GeometryDrawCounts {
        val counts = GeometryDrawCounts()
        for (feature in frame.projectedFeatures.sortedForChartReadability()) {
            when (val geometry = feature.geometry) {
                is ProjectedGeometry.Empty -> Unit
                is ProjectedGeometry.Point -> {
                    if (includePointGlyphs) {
                        if (feature.objectClass.uppercase() == "SOUNDG") {
                            if (includeSoundingPointGlyphs && drawSoundingLabel2d(context, geometry.point, feature)) counts.points++
                        } else {
                            drawPointGlyph2d(context, geometry.point, feature.objectClass, colorFor(feature.objectClass))
                            counts.points++
                        }
                    }
                }
                is ProjectedGeometry.MultiPoint -> {
                    if (includePointGlyphs) {
                        if (feature.objectClass.uppercase() == "SOUNDG") {
                            if (includeSoundingPointGlyphs) {
                                geometry.points.forEachIndexed { index, point ->
                                    if (drawSoundingLabel2d(context, point, feature, index)) counts.points++
                                }
                            }
                        } else {
                            geometry.points.forEach { point ->
                                drawPointGlyph2d(context, point, feature.objectClass, colorFor(feature.objectClass))
                            }
                            counts.points += geometry.points.size
                        }
                    }
                }
                is ProjectedGeometry.LineString -> {
                    if (geometry.points.size >= 2) {
                        strokePath2d(
                            context,
                            geometry.points,
                            colorFor(feature.objectClass),
                            lineWidthFor(feature.objectClass),
                            closePath = false
                        )
                        counts.lines++
                    }
                }
                is ProjectedGeometry.Polygon -> {
                    if (drawPolygon2d(context, geometry, feature.objectClass)) counts.areas++
                }
                is ProjectedGeometry.MultiPolygon -> geometry.polygons.forEach { polygon ->
                    if (drawPolygon2d(context, polygon, feature.objectClass)) counts.areas++
                }
            }
        }
        return counts
    }

    private fun drawPolygon2d(
        context: CanvasRenderingContext2D,
        polygon: ProjectedGeometry.Polygon,
        objectClass: String
    ): Boolean {
        val outer = polygon.rings.firstOrNull().orEmpty()
        if (outer.size < 3) return false
        fillPath2d(context, outer, fillColorFor(objectClass))
        polygon.rings.forEach { ring ->
            if (ring.size >= 2) {
                strokePath2d(context, ring.closedForStroke(), colorFor(objectClass), lineWidthFor(objectClass), closePath = false)
            }
        }
        return true
    }

    private fun drawSoundingLabel2d(
        context: CanvasRenderingContext2D,
        point: ScreenPoint,
        feature: ProjectedFeature,
        pointIndex: Int = 0
    ): Boolean {
        val label = feature.soundingLabel(pointIndex) ?: return false
        context.fillStyle = colorFor(feature.objectClass).toCssRgba()
        context.asDynamic().font = "11px monospace"
        context.asDynamic().textAlign = "center"
        context.asDynamic().textBaseline = "middle"
        context.fillText(label, point.x, point.y)
        return true
    }

    private fun drawPointGlyph2d(
        context: CanvasRenderingContext2D,
        point: ScreenPoint,
        objectClass: String,
        color: FloatArray
    ) {
        val normalized = objectClass.uppercase()
        val size = when (normalized) {
            "SOUNDG" -> 3.2
            "LIGHTS" -> 10.0
            "WRECKS", "OBSTRN" -> 9.0
            "BOYLAT", "BOYCAR", "BOYSAW", "BOYISD", "BOYSPP", "BOYINB",
            "BCNLAT", "BCNCAR", "BCNSAW", "BCNSPP", "BCNISD" -> 8.5
            else -> 7.5
        }
        val halo = floatArrayOf(1.0f, 1.0f, 1.0f, 0.92f)
        if (normalized != "SOUNDG") drawPointGlyphShape2d(context, point, objectClass, halo, size + 2.4)
        drawPointGlyphShape2d(context, point, objectClass, color, size)
        if (normalized in setOf("BOYLAT", "BOYCAR", "BOYSAW", "BOYISD", "BOYSPP", "BOYINB")) {
            strokePath2d(context, listOf(ScreenPoint(point.x, point.y + size), ScreenPoint(point.x, point.y + size * 1.9)), color, 1.5, closePath = false)
        }
        if (normalized in setOf("BCNLAT", "BCNCAR", "BCNSAW", "BCNSPP", "BCNISD")) {
            strokePath2d(context, listOf(ScreenPoint(point.x, point.y + size), ScreenPoint(point.x, point.y + size * 2.1)), color, 1.5, closePath = false)
            strokePath2d(context, listOf(ScreenPoint(point.x - size * 0.45, point.y + size * 2.1), ScreenPoint(point.x + size * 0.45, point.y + size * 2.1)), color, 1.5, closePath = false)
        }
    }

    private fun drawPointGlyphShape2d(
        context: CanvasRenderingContext2D,
        point: ScreenPoint,
        objectClass: String,
        color: FloatArray,
        size: Double
    ) {
        when (objectClass.uppercase()) {
            "BOYLAT", "BOYCAR", "BOYSAW", "BOYISD", "BOYSPP", "BOYINB" -> {
                val diamond = listOf(
                    ScreenPoint(point.x, point.y - size),
                    ScreenPoint(point.x + size, point.y),
                    ScreenPoint(point.x, point.y + size),
                    ScreenPoint(point.x - size, point.y)
                )
                fillPath2d(context, diamond, color.withAlpha(0.28f))
                strokePath2d(context, diamond, color, 1.6, closePath = true)
            }
            "BCNLAT", "BCNCAR", "BCNSAW", "BCNSPP", "BCNISD" -> {
                val triangle = listOf(
                    ScreenPoint(point.x, point.y - size),
                    ScreenPoint(point.x + size, point.y + size),
                    ScreenPoint(point.x - size, point.y + size)
                )
                fillPath2d(context, triangle, color.withAlpha(0.24f))
                strokePath2d(context, triangle, color, 1.6, closePath = true)
            }
            "LIGHTS" -> {
                fillCircle2d(context, point, size * 0.36, color.withAlpha(0.30f))
                strokePath2d(context, listOf(ScreenPoint(point.x - size, point.y), ScreenPoint(point.x + size, point.y)), color, 1.5, closePath = false)
                strokePath2d(context, listOf(ScreenPoint(point.x, point.y - size), ScreenPoint(point.x, point.y + size)), color, 1.5, closePath = false)
                strokePath2d(context, listOf(ScreenPoint(point.x - size * 0.7, point.y - size * 0.7), ScreenPoint(point.x + size * 0.7, point.y + size * 0.7)), color, 1.5, closePath = false)
                strokePath2d(context, listOf(ScreenPoint(point.x - size * 0.7, point.y + size * 0.7), ScreenPoint(point.x + size * 0.7, point.y - size * 0.7)), color, 1.5, closePath = false)
            }
            "WRECKS", "OBSTRN" -> {
                strokePath2d(context, listOf(ScreenPoint(point.x - size, point.y - size), ScreenPoint(point.x + size, point.y + size)), color, 1.0, closePath = false)
                strokePath2d(context, listOf(ScreenPoint(point.x - size, point.y + size), ScreenPoint(point.x + size, point.y - size)), color, 1.0, closePath = false)
            }
            "SOUNDG" -> fillCircle2d(context, point, size, color)
            else -> {
                fillCircle2d(context, point, size * 0.55, color.withAlpha(0.25f))
                strokePath2d(context, listOf(ScreenPoint(point.x - size, point.y), ScreenPoint(point.x + size, point.y)), color, 1.4, closePath = false)
                strokePath2d(context, listOf(ScreenPoint(point.x, point.y - size), ScreenPoint(point.x, point.y + size)), color, 1.4, closePath = false)
            }
        }
    }

    private fun drawCrosshair2d(context: CanvasRenderingContext2D, canvas: HTMLCanvasElement, size: Double) {
        val cx = canvas.width / 2.0
        val cy = canvas.height / 2.0
        val color = floatArrayOf(0.0f, 0.0f, 0.0f, 0.85f)
        strokePath2d(context, listOf(ScreenPoint(cx - size, cy), ScreenPoint(cx + size, cy)), color, 1.0, closePath = false)
        strokePath2d(context, listOf(ScreenPoint(cx, cy - size), ScreenPoint(cx, cy + size)), color, 1.0, closePath = false)
    }

    private fun fillPath2d(context: CanvasRenderingContext2D, points: List<ScreenPoint>, color: FloatArray) {
        if (points.size < 3) return
        context.beginPath()
        context.moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { point -> context.lineTo(point.x, point.y) }
        context.closePath()
        context.fillStyle = color.toCssRgba()
        context.fill()
    }

    private fun strokePath2d(context: CanvasRenderingContext2D, points: List<ScreenPoint>, color: FloatArray, lineWidth: Double, closePath: Boolean) {
        if (points.size < 2) return
        context.beginPath()
        context.moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { point -> context.lineTo(point.x, point.y) }
        if (closePath) context.closePath()
        context.strokeStyle = color.toCssRgba()
        context.lineWidth = lineWidth
        context.stroke()
    }

    private fun fillCircle2d(context: CanvasRenderingContext2D, center: ScreenPoint, radiusPx: Double, color: FloatArray) {
        context.beginPath()
        context.arc(center.x, center.y, radiusPx, 0.0, kotlin.math.PI * 2.0)
        context.fillStyle = color.toCssRgba()
        context.fill()
    }

    private fun drawDecodedGeometry(
        program: BrowserSimpleColorProgram,
        canvas: HTMLCanvasElement,
        frame: StaticChartFrame,
        includePointGlyphs: Boolean,
        includeSoundingPointGlyphs: Boolean
    ): GeometryDrawCounts {
        val counts = GeometryDrawCounts()
        for (feature in frame.projectedFeatures.sortedForChartReadability()) {
            when (val geometry = feature.geometry) {
                is ProjectedGeometry.Empty -> Unit
                is ProjectedGeometry.Point -> {
                    if (includePointGlyphs) {
                        if (feature.objectClass.uppercase() == "SOUNDG") {
                            if (includeSoundingPointGlyphs && drawSoundingLabel(program, geometry.point, canvas, feature)) counts.points++
                        } else {
                            drawPointGlyph(program, geometry.point, canvas, feature.objectClass, colorFor(feature.objectClass))
                            counts.points++
                        }
                    }
                }
                is ProjectedGeometry.MultiPoint -> {
                    if (includePointGlyphs) {
                        if (feature.objectClass.uppercase() == "SOUNDG") {
                            if (includeSoundingPointGlyphs) {
                                geometry.points.forEachIndexed { index, point ->
                                    if (drawSoundingLabel(program, point, canvas, feature, index)) counts.points++
                                }
                            }
                        } else {
                            geometry.points.forEach { point -> drawPointGlyph(program, point, canvas, feature.objectClass, colorFor(feature.objectClass)) }
                            counts.points += geometry.points.size
                        }
                    }
                }
                is ProjectedGeometry.LineString -> {
                    if (geometry.points.size >= 2) {
                        program.drawLineStrip(geometry.points, canvas, colorFor(feature.objectClass), lineWidthFor(feature.objectClass))
                        counts.lines++
                    }
                }
                is ProjectedGeometry.Polygon -> {
                    if (drawPolygon(program, canvas, geometry, feature.objectClass)) counts.areas++
                }
                is ProjectedGeometry.MultiPolygon -> geometry.polygons.forEach { polygon ->
                    if (drawPolygon(program, canvas, polygon, feature.objectClass)) counts.areas++
                }
            }
        }
        return counts
    }

    private fun drawPolygon(
        program: BrowserSimpleColorProgram,
        canvas: HTMLCanvasElement,
        polygon: ProjectedGeometry.Polygon,
        objectClass: String
    ): Boolean {
        val outer = polygon.rings.firstOrNull().orEmpty()
        if (outer.size < 3) return false
        program.drawTriangleFan(outer, canvas, fillColorFor(objectClass))
        polygon.rings.forEach { ring ->
            if (ring.size >= 2) program.drawLineStrip(ring.closedForStroke(), canvas, colorFor(objectClass), lineWidthFor(objectClass))
        }
        return true
    }

    private fun List<ScreenPoint>.closedForStroke(): List<ScreenPoint> =
        if (size >= 2 && first() != last()) this + first() else this


    private fun drawSoundingLabel(
        program: BrowserSimpleColorProgram,
        point: ScreenPoint,
        canvas: HTMLCanvasElement,
        feature: ProjectedFeature,
        pointIndex: Int = 0
    ): Boolean {
        val label = feature.soundingLabel(pointIndex) ?: return false
        program.drawSegmentLabel(label, point, canvas, colorFor(feature.objectClass), heightPx = 10.0, strokeWidthPx = 1.35)
        return true
    }

    private fun drawPointGlyph(
        program: BrowserSimpleColorProgram,
        point: ScreenPoint,
        canvas: HTMLCanvasElement,
        objectClass: String,
        color: FloatArray
    ) {
        val normalized = objectClass.uppercase()
        val size = when (normalized) {
            "SOUNDG" -> 3.2
            "LIGHTS" -> 10.0
            "WRECKS", "OBSTRN" -> 9.0
            "BOYLAT", "BOYCAR", "BOYSAW", "BOYISD", "BOYSPP", "BOYINB",
            "BCNLAT", "BCNCAR", "BCNSAW", "BCNSPP", "BCNISD" -> 8.5
            else -> 7.5
        }
        val halo = floatArrayOf(1.0f, 1.0f, 1.0f, 0.92f)
        if (normalized != "SOUNDG") drawPointGlyphShape(program, point, canvas, objectClass, halo, size + 2.4)
        drawPointGlyphShape(program, point, canvas, objectClass, color, size)
        if (normalized in setOf("BOYLAT", "BOYCAR", "BOYSAW", "BOYISD", "BOYSPP", "BOYINB")) {
            program.drawLineStrip(listOf(ScreenPoint(point.x, point.y + size), ScreenPoint(point.x, point.y + size * 1.9)), canvas, color, 1.5)
        }
        if (normalized in setOf("BCNLAT", "BCNCAR", "BCNSAW", "BCNSPP", "BCNISD")) {
            program.drawLineStrip(listOf(ScreenPoint(point.x, point.y + size), ScreenPoint(point.x, point.y + size * 2.1)), canvas, color, 1.5)
            program.drawLineStrip(listOf(ScreenPoint(point.x - size * 0.45, point.y + size * 2.1), ScreenPoint(point.x + size * 0.45, point.y + size * 2.1)), canvas, color, 1.5)
        }
    }

    private fun drawPointGlyphShape(
        program: BrowserSimpleColorProgram,
        point: ScreenPoint,
        canvas: HTMLCanvasElement,
        objectClass: String,
        color: FloatArray,
        size: Double
    ) {
        when (objectClass.uppercase()) {
            "BOYLAT", "BOYCAR", "BOYSAW", "BOYISD", "BOYSPP", "BOYINB" -> {
                val diamond = listOf(
                    ScreenPoint(point.x, point.y - size),
                    ScreenPoint(point.x + size, point.y),
                    ScreenPoint(point.x, point.y + size),
                    ScreenPoint(point.x - size, point.y),
                    ScreenPoint(point.x, point.y - size)
                )
                program.drawTriangleFan(diamond.dropLast(1), canvas, color.withAlpha(0.28f))
                program.drawLineStrip(diamond, canvas, color, 1.6)
            }
            "BCNLAT", "BCNCAR", "BCNSAW", "BCNSPP", "BCNISD" -> {
                val triangle = listOf(
                    ScreenPoint(point.x, point.y - size),
                    ScreenPoint(point.x + size, point.y + size),
                    ScreenPoint(point.x - size, point.y + size),
                    ScreenPoint(point.x, point.y - size)
                )
                program.drawTriangleFan(triangle.dropLast(1), canvas, color.withAlpha(0.24f))
                program.drawLineStrip(triangle, canvas, color, 1.6)
            }
            "LIGHTS" -> {
                program.drawCircle(point, size * 0.36, canvas, color.withAlpha(0.30f), 12)
                program.drawLineStrip(listOf(ScreenPoint(point.x - size, point.y), ScreenPoint(point.x + size, point.y)), canvas, color, 1.5)
                program.drawLineStrip(listOf(ScreenPoint(point.x, point.y - size), ScreenPoint(point.x, point.y + size)), canvas, color, 1.5)
                program.drawLineStrip(listOf(ScreenPoint(point.x - size * 0.7, point.y - size * 0.7), ScreenPoint(point.x + size * 0.7, point.y + size * 0.7)), canvas, color, 1.5)
                program.drawLineStrip(listOf(ScreenPoint(point.x - size * 0.7, point.y + size * 0.7), ScreenPoint(point.x + size * 0.7, point.y - size * 0.7)), canvas, color, 1.5)
            }
            "WRECKS", "OBSTRN" -> {
                program.drawLineStrip(listOf(ScreenPoint(point.x - size, point.y - size), ScreenPoint(point.x + size, point.y + size)), canvas, color)
                program.drawLineStrip(listOf(ScreenPoint(point.x - size, point.y + size), ScreenPoint(point.x + size, point.y - size)), canvas, color)
            }
            "SOUNDG" -> {
                program.drawCircle(point, size, canvas, color, 10)
            }
            else -> {
                program.drawCircle(point, size * 0.55, canvas, color.withAlpha(0.25f), 12)
                program.drawLineStrip(listOf(ScreenPoint(point.x - size, point.y), ScreenPoint(point.x + size, point.y)), canvas, color, 1.4)
                program.drawLineStrip(listOf(ScreenPoint(point.x, point.y - size), ScreenPoint(point.x, point.y + size)), canvas, color, 1.4)
            }
        }
    }

    private fun drawCrosshair(program: BrowserSimpleColorProgram, canvas: HTMLCanvasElement, size: Double) {
        val cx = canvas.width / 2.0
        val cy = canvas.height / 2.0
        val color = floatArrayOf(0.0f, 0.0f, 0.0f, 0.85f)
        program.drawLineStrip(listOf(ScreenPoint(cx - size, cy), ScreenPoint(cx + size, cy)), canvas, color)
        program.drawLineStrip(listOf(ScreenPoint(cx, cy - size), ScreenPoint(cx, cy + size)), canvas, color)
    }

    private fun colorFor(objectClass: String): FloatArray = when (objectClass.uppercase()) {
        "DEPCNT" -> floatArrayOf(0.13f, 0.35f, 0.56f, 1.0f)
        "COALNE", "SLOTOP" -> floatArrayOf(0.28f, 0.20f, 0.12f, 1.0f)
        "SOUNDG" -> floatArrayOf(0.12f, 0.16f, 0.20f, 0.95f)
        "BOYLAT", "BOYCAR", "BOYSAW", "BOYISD", "BOYSPP", "BOYINB",
        "BCNLAT", "BCNCAR", "BCNSAW", "BCNSPP", "BCNISD", "LIGHTS" -> floatArrayOf(0.85f, 0.1f, 0.05f, 1.0f)
        "WRECKS", "OBSTRN" -> floatArrayOf(0.45f, 0.1f, 0.1f, 1.0f)
        "CBLARE", "CBLOHD", "CBLSUB", "PIPARE", "PIPOHD", "PIPSOL" -> floatArrayOf(0.55f, 0.25f, 0.70f, 0.9f)
        else -> floatArrayOf(0.05f, 0.18f, 0.24f, 1.0f)
    }

    private fun fillColorFor(objectClass: String): FloatArray = when (objectClass.uppercase()) {
        "DEPARE", "DRGARE", "SEAARE", "UNSARE" -> floatArrayOf(0.72f, 0.87f, 0.94f, 0.62f)
        "LNDARE" -> floatArrayOf(0.92f, 0.85f, 0.70f, 0.86f)
        "LAKARE", "RIVERS" -> floatArrayOf(0.62f, 0.84f, 0.92f, 0.75f)
        "FAIRWY", "ACHARE" -> floatArrayOf(0.84f, 0.94f, 0.97f, 0.55f)
        "RESARE", "CBLARE", "PIPARE" -> floatArrayOf(0.92f, 0.84f, 0.95f, 0.35f)
        else -> floatArrayOf(0.78f, 0.86f, 0.80f, 0.45f)
    }

    private fun lineWidthFor(objectClass: String): Double = when (objectClass.uppercase()) {
        "COALNE", "SLOTOP" -> 2.5
        "DEPCNT" -> 1.4
        "CBLARE", "CBLOHD", "CBLSUB", "PIPARE", "PIPOHD", "PIPSOL" -> 1.2
        else -> 1.8
    }
}

private class GeometryDrawCounts(
    var points: Int = 0,
    var lines: Int = 0,
    var areas: Int = 0
) {
    fun toMessage(): String = "points=$points lines=$lines areas=$areas"
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
    fun drawLineStrip(points: List<ScreenPoint>, canvas: HTMLCanvasElement, color: FloatArray, lineWidth: Double = 1.0) = draw(WebGLRenderingContext.LINE_STRIP, points, canvas, color, lineWidth)

    fun drawTriangleFan(points: List<ScreenPoint>, canvas: HTMLCanvasElement, color: FloatArray) {
        if (points.size < 3) return
        draw(WebGLRenderingContext.TRIANGLE_FAN, points, canvas, color)
    }

    fun drawCircle(center: ScreenPoint, radiusPx: Double, canvas: HTMLCanvasElement, color: FloatArray, segments: Int = 16) {
        val points = mutableListOf(center)
        val segmentCount = segments.coerceAtLeast(8)
        for (index in 0..segmentCount) {
            val angle = (index.toDouble() / segmentCount.toDouble()) * kotlin.math.PI * 2.0
            points += ScreenPoint(center.x + kotlin.math.cos(angle) * radiusPx, center.y + kotlin.math.sin(angle) * radiusPx)
        }
        draw(WebGLRenderingContext.TRIANGLE_FAN, points, canvas, color)
    }

    fun drawSegmentLabel(label: String, center: ScreenPoint, canvas: HTMLCanvasElement, color: FloatArray, heightPx: Double, strokeWidthPx: Double) {
        val chars = label.filter { it.isDigit() || it == '.' || it == '-' }
        if (chars.isEmpty()) return
        val charWidth = heightPx * 0.56
        val advance = charWidth * 0.78
        val totalWidth = advance * (chars.length - 1).coerceAtLeast(0) + charWidth
        var x = center.x - totalWidth / 2.0
        val y = center.y - heightPx / 2.0
        chars.forEach { char ->
            drawSegmentChar(char, ScreenPoint(x, y), charWidth, heightPx, canvas, color, strokeWidthPx)
            x += advance
        }
    }

    private fun drawSegmentChar(char: Char, topLeft: ScreenPoint, width: Double, height: Double, canvas: HTMLCanvasElement, color: FloatArray, strokeWidthPx: Double) {
        val x = topLeft.x
        val y = topLeft.y
        val midY = y + height / 2.0
        val bottomY = y + height
        val rightX = x + width
        val inset = width * 0.10
        val segments = when (char) {
            '0' -> listOf('a', 'b', 'c', 'd', 'e', 'f')
            '1' -> listOf('b', 'c')
            '2' -> listOf('a', 'b', 'g', 'e', 'd')
            '3' -> listOf('a', 'b', 'g', 'c', 'd')
            '4' -> listOf('f', 'g', 'b', 'c')
            '5' -> listOf('a', 'f', 'g', 'c', 'd')
            '6' -> listOf('a', 'f', 'g', 'e', 'c', 'd')
            '7' -> listOf('a', 'b', 'c')
            '8' -> listOf('a', 'b', 'c', 'd', 'e', 'f', 'g')
            '9' -> listOf('a', 'b', 'c', 'd', 'f', 'g')
            '-' -> listOf('g')
            else -> emptyList()
        }
        for (segment in segments) {
            val points = when (segment) {
                'a' -> listOf(ScreenPoint(x + inset, y), ScreenPoint(rightX - inset, y))
                'b' -> listOf(ScreenPoint(rightX, y + inset), ScreenPoint(rightX, midY - inset))
                'c' -> listOf(ScreenPoint(rightX, midY + inset), ScreenPoint(rightX, bottomY - inset))
                'd' -> listOf(ScreenPoint(x + inset, bottomY), ScreenPoint(rightX - inset, bottomY))
                'e' -> listOf(ScreenPoint(x, midY + inset), ScreenPoint(x, bottomY - inset))
                'f' -> listOf(ScreenPoint(x, y + inset), ScreenPoint(x, midY - inset))
                'g' -> listOf(ScreenPoint(x + inset, midY), ScreenPoint(rightX - inset, midY))
                else -> emptyList()
            }
            drawLineStrip(points, canvas, color, strokeWidthPx)
        }
        if (char == '.') {
            drawCircle(ScreenPoint(x + width / 2.0, bottomY - height * 0.08), height * 0.065, canvas, color, segments = 8)
        }
    }

    private fun draw(mode: Int, points: List<ScreenPoint>, canvas: HTMLCanvasElement, color: FloatArray, lineWidth: Double = 1.0) {
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
        if (mode == WebGLRenderingContext.LINE_STRIP) gl.lineWidth(lineWidth.toFloat())
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

private fun FloatArray.withAlpha(alpha: Float): FloatArray = floatArrayOf(this[0], this[1], this[2], alpha)

private fun FloatArray.toCssRgba(): String {
    val red = (this[0].coerceIn(0.0f, 1.0f) * 255.0f).toInt()
    val green = (this[1].coerceIn(0.0f, 1.0f) * 255.0f).toInt()
    val blue = (this[2].coerceIn(0.0f, 1.0f) * 255.0f).toInt()
    val alpha = this[3].coerceIn(0.0f, 1.0f)
    return "rgba($red, $green, $blue, $alpha)"
}
