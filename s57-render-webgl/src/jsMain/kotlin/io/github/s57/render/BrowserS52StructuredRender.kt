package io.github.s57.render

import io.github.s52.render.webgl.RenderViewport
import io.github.s52.render.webgl.WebGlS52Renderer
import kotlinx.browser.document
import kotlinx.browser.window
import kotlin.js.console
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement

fun BrowserS57WebGlRenderer.renderS52FrameWithSummary(
    canvasId: String,
    frame: StaticChartFrame
): RenderedFrameSummary {
    val canvas = document.getElementById(canvasId) as? HTMLCanvasElement
        ?: return RenderedFrameSummary(0, 0, "Canvas '$canvasId' not found", frame.request.camera)

    if (canvas.getContext("webgl2") == null) {
        val s52 = S52RenderSummary(failureStage = "webgl2")
        return renderS52FailureFrame(
            canvasId = canvasId,
            frame = frame,
            reason = "S-52 WebGL render failed: WebGL2 is not available",
            s52 = s52
        )
    }

    val sourceFeatures = frame.projectedFeatures.mapNotNull { it.feature }
    if (sourceFeatures.isEmpty()) {
        val s52 = S52RenderSummary(failureStage = "projection")
        return renderS52FailureFrame(
            canvasId = canvasId,
            frame = frame,
            reason = "S-52 render skipped: projectedSourceFeatures=0 queried=" + frame.queriedFeatureCount + " adapted=" + frame.adaptedFeatureCount,
            s52 = s52
        )
    }

    val bridge = BrowserS52Bridge()
    val portrayed = bridge.portray(
        features = sourceFeatures,
        paletteName = frame.request.paletteName,
        scaleDenominator = frame.request.scaleDenominator
    )
    portrayed.diagnostics.logRenderDiagnosticsToConsole()

    if (portrayed.commands.isEmpty()) {
        val s52 = portrayed.toSummary(failureStage = "portrayal")
        return renderS52FailureFrame(
            canvasId = canvasId,
            frame = frame,
            reason = "S-52 portrayal produced zero commands: profile=" + portrayed.profile + " encFeatures=" + portrayed.featureCount + " diagnostics=" + portrayed.diagnostics.size,
            s52 = s52
        )
    }

    val viewport = RenderViewport(
        west = frame.request.bounds.minLon,
        south = frame.request.bounds.minLat,
        east = frame.request.bounds.maxLon,
        north = frame.request.bounds.maxLat
    )
    val linearOrAreaFeatureCount = frame.projectedLinearOrAreaFeatureCount()
    window.asDynamic().s57S52ResourceRenderReady = false
    window.asDynamic().s57S52RasterCommandCount = portrayed.rasterCommandCount
    window.asDynamic().s57S52InitialDrawCalls = 0
    window.asDynamic().s57S52LastResourceDrawCalls = 0

    return try {
        var renderer: WebGlS52Renderer? = null
        renderer = WebGlS52Renderer(canvas, bridge.presLib) {
            val readyRenderer = renderer ?: return@WebGlS52Renderer
            try {
                val readyStats = readyRenderer.render(portrayed.commands, portrayed.settings, viewport)
                window.asDynamic().s57S52ResourceRenderReady = true
                window.asDynamic().s57S52LastResourceDrawCalls = readyStats.drawCalls
                val previousReadyRenderCount =
                    (window.asDynamic().s57S52ResourceRenderCount as? Number)?.toInt() ?: 0
                window.asDynamic().s57S52ResourceRenderCount = previousReadyRenderCount + 1
                val readySummary = portrayed.toSummary(drawCallCount = readyStats.drawCalls)
                if (readySummary.shouldOverlayDecodedGeometry(sourceFeatures.size, linearOrAreaFeatureCount)) {
                    // Intentionally disabled by policy.  The decoded renderer is a
                    // parser/debug view, not an OpenCPN presentation fallback.
                    console.warn("S-52 decoded-geometry overlay was requested but suppressed")
                }
            } catch (_: Throwable) {
                // The initial render path already reports errors. Resource-ready
                // callbacks must never break the browser event loop.
            }
        }
        val activeRenderer = renderer
        val stats = activeRenderer.render(portrayed.commands, portrayed.settings, viewport)
        window.asDynamic().s57S52InitialDrawCalls = stats.drawCalls
        val s52 = portrayed.toSummary(drawCallCount = stats.drawCalls)
        val message = "S-52 WebGL rendered profile=" + portrayed.profile +
            " encFeatures=" + portrayed.featureCount +
            " commands=" + portrayed.commands.size +
            " rasterCommands=" + portrayed.rasterCommandCount +
            " drawCalls=" + stats.drawCalls +
            " symbols=" + stats.symbolCount +
            " lines=" + stats.lineCount +
            " areas=" + (stats.areaFillCount + stats.areaPatternCount) +
            " text=" + (stats.textCount + stats.soundingCount) +
            " diagnostics=" + portrayed.diagnostics.size
        val renderedLinearOrAreaDrawCount = stats.lineCount + stats.areaFillCount + stats.areaPatternCount
        val missingLinearOrAreaOutput = linearOrAreaFeatureCount > 0 && renderedLinearOrAreaDrawCount <= 0
        val effectiveS52 = if (missingLinearOrAreaOutput) {
            s52.withPartialLineAreaDiagnostic(
                frame = frame,
                sourceFeatureCount = sourceFeatures.size,
                linearOrAreaFeatureCount = linearOrAreaFeatureCount,
                renderedLinearOrAreaDrawCount = renderedLinearOrAreaDrawCount
            )
        } else {
            s52
        }
        val waitingForRasterAssets = stats.drawCalls <= 0 && portrayed.rasterCommandCount > 0
        val effectiveMessage = when {
            waitingForRasterAssets -> message + "; waiting for OpenCPN raster atlas resources to load asynchronously"
            missingLinearOrAreaOutput -> message + "; S-52 emitted no line/area draw calls for source line/area features=" + linearOrAreaFeatureCount +
                " but decoded debug geometry fallback is suppressed"
            else -> message
        }
        if (s52.needsGeometryFallback(sourceFeatures.size, linearOrAreaFeatureCount)) {
            renderS52FailureFrame(
                canvasId = canvasId,
                frame = frame,
                reason = effectiveMessage + "; S-52 produced no usable draw calls",
                s52 = effectiveS52.copy(failureStage = "zero-drawcalls")
            )
        } else {
            frame.summary().copy(
                message = effectiveMessage,
                s52 = effectiveS52,
                pipelineDiagnostics = effectiveS52.diagnostics
            )
        }
    } catch (t: Throwable) {
        val s52 = portrayed.toSummary(failureStage = "webgl-render")
        renderS52FailureFrame(
            canvasId = canvasId,
            frame = frame,
            reason = "S-52 WebGL render failed after portrayal: " + (t.message ?: t.toString()) +
                " encFeatures=" + portrayed.featureCount +
                " commands=" + portrayed.commands.size,
            s52 = s52
        )
    }
}

/**
 * Clear the canvas and report the S-52 failure without invoking renderFrame().
 *
 * renderFrame() is the decoded-geometry debug renderer.  Calling it from the
 * S-52 path is what makes Chrome/PNG snapshots show red diamonds, plus signs,
 * and repository fallback colors instead of OpenCPN symbols.  A failed S-52
 * frame should be visibly empty and diagnostic-rich rather than silently
 * replacing the presentation library with debug glyphs.
 */
fun BrowserS57WebGlRenderer.renderS52FailureFrame(
    canvasId: String,
    frame: StaticChartFrame,
    reason: String,
    s52: S52RenderSummary
): RenderedFrameSummary {
    val canvas = document.getElementById(canvasId) as? HTMLCanvasElement
    if (canvas != null) clearCanvasForSuppressedS52Fallback(canvas)
    val diagnostic = RenderPipelineDiagnostic(
        stage = if (s52.failureStage == "portrayal") RenderPipelineStage.S52Portrayal else RenderPipelineStage.WebGl,
        severity = RenderPipelineSeverity.Error,
        code = "s52.debug_geometry_fallback_suppressed",
        message = reason + "; decoded debug geometry fallback was suppressed",
        source = RenderPipelineSource(cellId = frame.request.cellId),
        metadata = mapOf("failureStage" to s52.failureStage)
    )
    diagnostic.logRenderDiagnosticToConsole()
    val diagnostics = s52.diagnostics + diagnostic
    return RenderedFrameSummary(
        widthPx = canvas?.width ?: frame.request.widthPx,
        heightPx = canvas?.height ?: frame.request.heightPx,
        message = reason + "; decoded debug geometry fallback suppressed",
        camera = frame.request.camera,
        centerCrosshairHits = frame.summary().centerCrosshairHits,
        depthMeshEnabled = frame.summary().depthMeshEnabled,
        s52 = s52.copy(diagnostics = diagnostics, diagnosticCount = diagnostics.size),
        pipelineDiagnostics = diagnostics
    )
}

private fun clearCanvasForSuppressedS52Fallback(canvas: HTMLCanvasElement) {
    val rawGl = canvas.getContext("webgl2") ?: canvas.getContext("webgl")
    if (rawGl != null) {
        val gl = rawGl.asDynamic()
        gl.viewport(0, 0, canvas.width, canvas.height)
        gl.clearColor(0.92, 0.96, 0.99, 1.0)
        gl.clear(0x4000)
        return
    }
    val ctx = canvas.getContext("2d") as? CanvasRenderingContext2D
    if (ctx != null) {
        ctx.fillStyle = "rgba(235, 245, 252, 1.0)"
        ctx.fillRect(0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
    }
}

private fun S52RenderSummary.withPartialLineAreaDiagnostic(
    frame: StaticChartFrame,
    sourceFeatureCount: Int,
    linearOrAreaFeatureCount: Int,
    renderedLinearOrAreaDrawCount: Int
): S52RenderSummary {
    val diagnostic = RenderPipelineDiagnostic(
        stage = RenderPipelineStage.S52Portrayal,
        severity = RenderPipelineSeverity.Warning,
        code = "s52.partial_line_area_output",
        message = "S-52 emitted draw calls, but line/area output is absent; keeping OpenCPN/S-52 output and suppressing decoded debug geometry fallback",
        source = RenderPipelineSource(cellId = frame.request.cellId),
        metadata = mapOf(
            "sourceFeatures" to sourceFeatureCount.toString(),
            "linearOrAreaFeatures" to linearOrAreaFeatureCount.toString(),
            "linearOrAreaDrawCalls" to renderedLinearOrAreaDrawCount.toString()
        )
    )
    diagnostic.logRenderDiagnosticToConsole()
    val nextDiagnostics = diagnostics + diagnostic
    return copy(diagnostics = nextDiagnostics, diagnosticCount = nextDiagnostics.size)
}

private fun List<RenderPipelineDiagnostic>.logRenderDiagnosticsToConsole() {
    forEach { it.logRenderDiagnosticToConsole() }
}

private fun RenderPipelineDiagnostic.logRenderDiagnosticToConsole() {
    when (severity) {
        RenderPipelineSeverity.Error -> console.error(toPlainText())
        RenderPipelineSeverity.Warning -> console.warn(toPlainText())
        RenderPipelineSeverity.Info -> Unit
    }
}

private fun StaticChartFrame.projectedLinearOrAreaFeatureCount(): Int = projectedFeatures.count { feature ->
    when (feature.geometry) {
        is ProjectedGeometry.LineString,
        is ProjectedGeometry.Polygon,
        is ProjectedGeometry.MultiPolygon -> true
        else -> false
    }
}
