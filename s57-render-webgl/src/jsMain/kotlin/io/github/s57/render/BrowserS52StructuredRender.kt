package io.github.s57.render

import io.github.s52.render.webgl.RenderViewport
import io.github.s52.render.webgl.WebGlS52Renderer
import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement

fun BrowserS57WebGlRenderer.renderS52FrameWithSummary(
    canvasId: String,
    frame: StaticChartFrame
): RenderedFrameSummary {
    val canvas = document.getElementById(canvasId) as? HTMLCanvasElement
        ?: return RenderedFrameSummary(0, 0, "Canvas '$canvasId' not found", frame.request.camera)

    if (canvas.getContext("webgl2") == null) {
        val s52 = S52RenderSummary(failureStage = "webgl2")
        return geometryFallbackRender(
            canvasId = canvasId,
            frame = frame,
            reason = "S-52 WebGL render failed: WebGL2 is not available",
            s52 = s52
        )
    }

    val sourceFeatures = frame.projectedFeatures.mapNotNull { it.feature }
    if (sourceFeatures.isEmpty()) {
        return frame.summary().copy(
            message = "S-52 render skipped: projectedSourceFeatures=0 queried=" + frame.queriedFeatureCount + " adapted=" + frame.adaptedFeatureCount,
            s52 = S52RenderSummary(failureStage = "projection")
        )
    }

    val bridge = BrowserS52Bridge()
    val portrayed = bridge.portray(
        features = sourceFeatures,
        paletteName = frame.request.paletteName,
        scaleDenominator = frame.request.scaleDenominator
    )

    if (portrayed.commands.isEmpty()) {
        val s52 = portrayed.toSummary(failureStage = "portrayal")
        return geometryFallbackRender(
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

    return try {
        var renderer: WebGlS52Renderer? = null
        renderer = WebGlS52Renderer(canvas, bridge.presLib) {
            val readyRenderer = renderer ?: return@WebGlS52Renderer
            try {
                readyRenderer.render(portrayed.commands, portrayed.settings, viewport)
            } catch (_: Throwable) {
                // The initial render path already reports errors. Resource-ready
                // callbacks must never break the browser event loop.
            }
        }
        val activeRenderer = renderer ?: throw IllegalStateException("S-52 renderer was not initialized")
        val stats = activeRenderer.render(portrayed.commands, portrayed.settings, viewport)
        val s52 = portrayed.toSummary(drawCallCount = stats.drawCalls)
        val message = "S-52 WebGL rendered profile=" + portrayed.profile +
            " encFeatures=" + portrayed.featureCount +
            " commands=" + portrayed.commands.size +
            " drawCalls=" + stats.drawCalls +
            " symbols=" + stats.symbolCount +
            " lines=" + stats.lineCount +
            " areas=" + (stats.areaFillCount + stats.areaPatternCount) +
            " text=" + (stats.textCount + stats.soundingCount) +
            " diagnostics=" + portrayed.diagnostics.size
        if (s52.needsGeometryFallback(sourceFeatures.size)) {
            geometryFallbackRender(
                canvasId = canvasId,
                frame = frame,
                reason = message + " but produced no visible draw calls",
                s52 = s52.copy(failureStage = "zero-drawcalls")
            )
        } else {
            frame.summary().copy(message = message, s52 = s52)
        }
    } catch (t: Throwable) {
        val s52 = portrayed.toSummary(failureStage = "webgl-render")
        geometryFallbackRender(
            canvasId = canvasId,
            frame = frame,
            reason = "S-52 WebGL render failed after portrayal: " + (t.message ?: t.toString()) +
                " encFeatures=" + portrayed.featureCount +
                " commands=" + portrayed.commands.size,
            s52 = s52
        )
    }
}

private fun BrowserS57WebGlRenderer.geometryFallbackRender(
    canvasId: String,
    frame: StaticChartFrame,
    reason: String,
    s52: S52RenderSummary
): RenderedFrameSummary {
    val fallback = renderFrame(canvasId, frame)
    return fallback.copy(
        message = s52FallbackMessage(reason, fallback.message),
        s52 = s52
    )
}
