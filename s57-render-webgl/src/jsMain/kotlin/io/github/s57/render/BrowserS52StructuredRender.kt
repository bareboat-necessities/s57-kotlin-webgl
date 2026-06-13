package io.github.s57.render

import io.github.s52.core.draw.S52DrawCommand
import io.github.s52.core.settings.MarinerSettings
import io.github.s52.render.webgl.RenderStats
import io.github.s52.render.webgl.RenderViewport
import io.github.s52.render.webgl.WebGlS52Renderer
import kotlinx.browser.document
import kotlinx.browser.window
import kotlin.js.console
import kotlin.math.roundToInt
import org.w3c.dom.HTMLCanvasElement

private val sharedS52Bridge: BrowserS52Bridge by lazy { BrowserS52Bridge() }
private val webGlS52RendererCache = mutableMapOf<String, CachedWebGlS52Renderer>()
private val s52PortrayalCache = linkedMapOf<S52PortrayalCacheKey, BrowserS52PortrayalResult>()
private const val MaxS52PortrayalCacheEntries: Int = 24

private data class S52PortrayalCacheKey(
    val featureIds: List<Long>,
    val paletteName: String,
    val scaleBucket: Int
)

private class CachedWebGlS52Renderer(
    val canvasId: String,
    val canvas: HTMLCanvasElement
) {
    lateinit var renderer: WebGlS52Renderer
    var commands: List<S52DrawCommand> = emptyList()
    var settings: MarinerSettings? = null
    var viewport: RenderViewport? = null
    private var renderInProgress: Boolean = false
    private var pendingResourceRender: Boolean = false
    private var resourceRenderScheduled: Boolean = false
    var textCommands: List<S52DrawCommand> = emptyList()
    private var textPostpass: BrowserS52WebGlTextPostpass? = null
    private var suppressedReentrantResourceCallbacks: Int = 0

    fun renderCurrentFrame(): io.github.s52.render.webgl.RenderStats {
        renderInProgress = true
        return try {
            val activeSettings = settings ?: error("S-52 settings are not available")
            val activeViewport = viewport ?: error("S-52 viewport is not available")
            renderWebGlOnly(activeSettings, activeViewport)
        } finally {
            renderInProgress = false
            scheduleResourceRenderIfNeeded()
        }
    }

    fun renderReadyResources() {
        if (!::renderer.isInitialized || commands.isEmpty()) return
        pendingResourceRender = true
        if (renderInProgress) {
            suppressedReentrantResourceCallbacks++
        }
        scheduleResourceRenderIfNeeded()
    }


    private fun renderWebGlOnly(
        activeSettings: MarinerSettings,
        activeViewport: RenderViewport
    ): RenderStats {
        val baseStats = renderer.render(commands, activeSettings, activeViewport)
        val textStats = textPostpass?.render(textCommands, activeSettings, activeViewport) ?: BrowserS52TextPostpassStats()
        return baseStats.copy(
            textCount = baseStats.textCount + textStats.textCount,
            soundingCount = baseStats.soundingCount + textStats.soundingCount,
            drawCalls = baseStats.drawCalls + textStats.drawCalls
        )
    }

    fun ensureTextPostpass(canvas: HTMLCanvasElement, presLib: io.github.s52.preslib.PresLibPack) {
        if (textPostpass == null) textPostpass = BrowserS52WebGlTextPostpass(canvas, presLib)
    }

    private fun scheduleResourceRenderIfNeeded() {
        if (!pendingResourceRender || resourceRenderScheduled) return
        if (!::renderer.isInitialized || commands.isEmpty() || settings == null || viewport == null) return
        resourceRenderScheduled = true
        window.setTimeout({
            resourceRenderScheduled = false
            if (renderInProgress) {
                suppressedReentrantResourceCallbacks++
                scheduleResourceRenderIfNeeded()
            } else {
                renderReadyResourcesAfterCurrentFrame()
            }
        }, 0)
    }

    private fun renderReadyResourcesAfterCurrentFrame() {
        val activeSettings = settings ?: return
        val activeViewport = viewport ?: return
        if (!::renderer.isInitialized || commands.isEmpty()) return
        pendingResourceRender = false
        renderInProgress = true
        try {
            val readyStats = renderWebGlOnly(activeSettings, activeViewport)
            window.asDynamic().s57S52ResourceRenderReady = true
            window.asDynamic().s57S52LastResourceDrawCalls = readyStats.drawCalls
            val previousReadyRenderCount =
                (window.asDynamic().s57S52ResourceRenderCount as? Number)?.toInt() ?: 0
            window.asDynamic().s57S52ResourceRenderCount = previousReadyRenderCount + 1
            window.asDynamic().s57S52SuppressedReentrantResourceCallbacks = suppressedReentrantResourceCallbacks
        } catch (_: Throwable) {
            // Resource-ready callbacks must never break the browser event loop.
        } finally {
            renderInProgress = false
        }
    }
}

fun BrowserS57WebGlRenderer.renderS52FrameWithSummary(
    canvasId: String,
    frame: StaticChartFrame
): RenderedFrameSummary {
    var canvas = document.getElementById(canvasId) as? HTMLCanvasElement
        ?: return RenderedFrameSummary(0, 0, "Canvas '$canvasId' not found", frame.request.camera)

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

    val bridge = sharedS52Bridge
    val portrayalCacheKey = S52PortrayalCacheKey(
        featureIds = sourceFeatures.map { it.id },
        paletteName = frame.request.paletteName,
        scaleBucket = (frame.request.scaleDenominator / 100.0).roundToInt()
    )
    val cachedPortrayal = s52PortrayalCache[portrayalCacheKey]
    val portrayed = cachedPortrayal ?: bridge.portray(
        features = sourceFeatures,
        paletteName = frame.request.paletteName,
        scaleDenominator = frame.request.scaleDenominator
    ).also { result ->
        if (s52PortrayalCache.size >= MaxS52PortrayalCacheEntries) {
            val oldest = s52PortrayalCache.keys.firstOrNull()
            if (oldest != null) s52PortrayalCache.remove(oldest)
        }
        s52PortrayalCache[portrayalCacheKey] = result
        result.diagnostics.logRenderDiagnosticsToConsole()
    }

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
    val displayPlan = buildBrowserS52DisplayCommandPlan(
        commands = portrayed.commands,
        sourceFeatures = sourceFeatures,
        presLib = bridge.presLib,
        viewport = viewport,
        widthPx = frame.request.widthPx,
        heightPx = frame.request.heightPx,
        scaleDenominator = frame.request.scaleDenominator
    )
    val displayDiagnostics = displayPlan.diagnostics(frame.request.cellId)
    window.asDynamic().s57S52ResourceRenderReady = false
    window.asDynamic().s57S52RasterCommandCount = portrayed.rasterCommandCount
    window.asDynamic().s57S52DisplayCommandCount = displayPlan.commands.size
    window.asDynamic().s57S52WebGlTextCommandCount = displayPlan.textCommands.size
    window.asDynamic().s57S52SuppressedRasterAreaPatternCount = displayPlan.suppressedRasterAreaPatternCount
    window.asDynamic().s57S52SuppressedDuplicatePointSymbolCount = displayPlan.suppressedDuplicatePointSymbolCount
    window.asDynamic().s57S52InitialDrawCalls = 0
    window.asDynamic().s57S52LastResourceDrawCalls = 0
    window.asDynamic().s57S52SuppressedReentrantResourceCallbacks = 0
    window.asDynamic().s57S52ContextResetRetry = false

    fun createRendererEntry(activeCanvas: HTMLCanvasElement): CachedWebGlS52Renderer =
        CachedWebGlS52Renderer(canvasId, activeCanvas).also { entry ->
            entry.renderer = WebGlS52Renderer(activeCanvas, bridge.presLib) {
                entry.renderReadyResources()
            }
            entry.ensureTextPostpass(activeCanvas, bridge.presLib)
        }

    fun rendererFor(activeCanvas: HTMLCanvasElement): CachedWebGlS52Renderer {
        val cached = webGlS52RendererCache[canvasId]
        if (cached != null && cached.canvas === activeCanvas) return cached
        val created = createRendererEntry(activeCanvas)
        webGlS52RendererCache[canvasId] = created
        return created
    }

    fun renderWith(activeCanvas: HTMLCanvasElement): RenderStats {
        val cachedRenderer = rendererFor(activeCanvas)
        cachedRenderer.ensureTextPostpass(activeCanvas, bridge.presLib)
        cachedRenderer.commands = displayPlan.commands
        cachedRenderer.textCommands = displayPlan.textCommands
        cachedRenderer.settings = portrayed.settings
        cachedRenderer.viewport = viewport
        return cachedRenderer.renderCurrentFrame()
    }

    return try {
        installWebGl2KotlinJsCompatibilityShim()
        /*
         * Let WebGlS52Renderer request the chart WebGL2 context first on the
         * normal path. If a stale/lost/poisoned canvas rejects WebGL2, retry
         * once on a replacement DOM canvas with the same id and dimensions.
         */
        var usedContextResetRetry = false
        val stats = try {
            renderWith(canvas)
        } catch (first: Throwable) {
            if (!first.isWebGl2ContextAvailabilityFailure()) throw first
            webGlS52RendererCache.remove(canvasId)
            usedContextResetRetry = true
            window.asDynamic().s57S52ContextResetRetry = true
            val currentCanvas = (document.getElementById(canvasId) as? HTMLCanvasElement) ?: canvas
            val replacementCanvas = replaceCanvasForS52WebGl2(canvasId, currentCanvas)
            installWebGl2KotlinJsCompatibilityShim()
            renderWith(replacementCanvas)
        }
        window.asDynamic().s57S52InitialDrawCalls = stats.drawCalls
        val s52Base = portrayed.toSummary(drawCallCount = stats.drawCalls)
        val s52 = s52Base.copy(
            diagnostics = s52Base.diagnostics + displayDiagnostics,
            diagnosticCount = s52Base.diagnostics.size + displayDiagnostics.size
        )
        val message = "S-52 WebGL rendered profile=" + portrayed.profile +
            " encFeatures=" + portrayed.featureCount +
            " commands=" + portrayed.commands.size +
            " webGlCommands=" + displayPlan.commands.size +
            " webGlTextCommands=" + displayPlan.textCommands.size +
            " rasterCommands=" + portrayed.rasterCommandCount +
            " suppressedRasterAreaPatterns=" + displayPlan.suppressedRasterAreaPatternCount +
            " suppressedDuplicatePointSymbols=" + displayPlan.suppressedDuplicatePointSymbolCount +
            " reentrantResourceCallbacks=" + ((window.asDynamic().s57S52SuppressedReentrantResourceCallbacks as? Number)?.toInt() ?: 0) +
            " contextResetRetry=" + usedContextResetRetry +
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
            waitingForRasterAssets -> message + "; waiting for S-52 asynchronous resource redraw"
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
        val webglReason = "S-52 WebGL render failed after portrayal: " + (t.message ?: t.toString()) +
            " encFeatures=" + portrayed.featureCount +
            " commands=" + portrayed.commands.size +
            " webgl2Shim=" + webGl2KotlinJsCompatibilityShimStatus() +
            "; no Canvas2D or decoded-geometry fallback is allowed"
        val s52 = portrayed.toSummary(failureStage = "webgl-render")
        renderS52FailureFrame(
            canvasId = canvasId,
            frame = frame,
            reason = webglReason,
            s52 = s52
        )
    }
}


private fun replaceCanvasForS52WebGl2(canvasId: String, canvas: HTMLCanvasElement): HTMLCanvasElement {
    /*
     * A canvas can permanently reject webgl2 after earlier demo/debug code has
     * claimed it with webgl1/2D, after context loss, or after a failed context
     * attribute negotiation.  The S-52 retry path must not inherit that poisoned
     * canvas.  Replace only the DOM element, keeping id/class/size/style, and let
     * WebGlS52Renderer create a fresh WebGL2 context on the replacement.
     */
    val replacement = (canvas.ownerDocument?.createElement("canvas") as? HTMLCanvasElement)
        ?: return canvas
    replacement.id = canvas.id.ifBlank { canvasId }
    replacement.className = canvas.className
    replacement.width = canvas.width
    replacement.height = canvas.height
    val style = canvas.getAttribute("style")
    if (style != null) replacement.setAttribute("style", style)
    replacement.setAttribute("data-s57-s52-context-reset", "webgl2")
    canvas.parentNode?.replaceChild(replacement, canvas)
    webGlS52RendererCache.remove(canvasId)
    dispatchChartCanvasReplacedEvent(canvasId)
    return replacement
}

private fun dispatchChartCanvasReplacedEvent(canvasId: String) {
    try {
        val event = js("typeof CustomEvent === 'function' ? new CustomEvent('s57-chart-canvas-replaced', { detail: {} }) : null")
        if (event != null) {
            event.asDynamic().detail.canvasId = canvasId
            window.asDynamic().dispatchEvent(event)
        }
    } catch (_: Throwable) {
        // Rebinding browser input is best-effort; rendering must not fail here.
    }
}

private fun Throwable.isWebGl2ContextAvailabilityFailure(): Boolean {
    val text = (message ?: toString()).lowercase()
    return "webgl2" in text || "context lost" in text || "context" in text && "available" in text
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
    // Intentionally do not claim any non-S-52 rendering context here.
    // If WebGL2 failed because the CI/browser backend was not ready, claiming
    // the chart canvas with WebGL1 or Canvas2D permanently prevents a later
    // S-52 WebGL2 retry from succeeding on the same DOM canvas.
    canvas.setAttribute("data-s52-fallback-suppressed", "true")
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
