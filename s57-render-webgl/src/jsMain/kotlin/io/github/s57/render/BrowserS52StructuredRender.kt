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
        return frame.summary().copy(
            message = "S-52 WebGL render failed: WebGL2 is not available",
            s52 = S52RenderSummary(failureStage = "webgl2")
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
        return frame.summary().copy(
            message = "S-52 portrayal produced zero commands: profile=" + portrayed.profile + " encFeatures=" + portrayed.featureCount + " diagnostics=" + portrayed.diagnostics.size,
            s52 = portrayed.toSummary(failureStage = "portrayal")
        )
    }

    val viewport = RenderViewport(
        west = frame.request.bounds.minLon,
        south = frame.request.bounds.minLat,
        east = frame.request.bounds.maxLon,
        north = frame.request.bounds.maxLat
    )

    return try {
        val renderer = WebGlS52Renderer(canvas, bridge.presLib)
        val stats = renderer.render(portrayed.commands, portrayed.settings, viewport)
        frame.summary().copy(
            message = "S-52 WebGL rendered profile=" + portrayed.profile +
                " encFeatures=" + portrayed.featureCount +
                " commands=" + portrayed.commands.size +
                " drawCalls=" + stats.drawCalls +
                " symbols=" + stats.symbolCount +
                " lines=" + stats.lineCount +
                " areas=" + (stats.areaFillCount + stats.areaPatternCount) +
                " text=" + (stats.textCount + stats.soundingCount) +
                " diagnostics=" + portrayed.diagnostics.size,
            s52 = portrayed.toSummary(drawCallCount = stats.drawCalls)
        )
    } catch (t: Throwable) {
        frame.summary().copy(
            message = "S-52 WebGL render failed after portrayal: " + (t.message ?: t.toString()) +
                " encFeatures=" + portrayed.featureCount +
                " commands=" + portrayed.commands.size,
            s52 = portrayed.toSummary(failureStage = "webgl-render")
        )
    }
}
