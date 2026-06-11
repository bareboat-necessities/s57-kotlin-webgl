package io.github.s57.render

import io.github.s57.core.GeoPoint
import io.github.s57.core.S57CellSummary
import io.github.s57.core.S57Dataset
import io.github.s57.core.import.S57ImportPipeline
import io.github.s57.core.import.S57ImportResult
import io.github.s57.index.InMemoryS57IndexStore
import io.github.s57.index.S57IndexImportReport
import io.github.s57.index.S57IndexStats
import io.github.s57.index.S57IndexStore

/**
 * Phase 9 high-level library facade for the core static-import/index/render path.
 *
 * This is still not a full chartplotter. It exposes the minimal engine surface
 * a larger app can build on: import decoded datasets, list cached cells, render a
 * fixed chart request, analyze the frame, and query what the center crosshair is
 * pointing at.
 */
class S57WebGlEngine(
    private val indexStore: S57IndexStore = InMemoryS57IndexStore(),
    private val staticRenderer: S57StaticChartRenderer = S57StaticChartRenderer(indexStore),
    private val importPipeline: S57ImportPipeline = S57ImportPipeline()
) {
    fun importS57Bytes(bytes: ByteArray): S57EngineImportResult {
        val decodeStart = monotonicNowMs()
        val imported = importPipeline.importBytes(bytes)
        val decodeMs = monotonicNowMs() - decodeStart
        return importDataset(imported.dataset, imported, decodeMs)
    }

    fun importS57ByteSequence(payloads: List<ByteArray>): S57EngineImportResult {
        val decodeStart = monotonicNowMs()
        val imported = importPipeline.importByteSequence(payloads)
        val decodeMs = monotonicNowMs() - decodeStart
        return importDataset(imported.dataset, imported, decodeMs)
    }

    fun importDataset(dataset: S57Dataset): S57EngineImportResult {
        return importDataset(dataset, null, decodeMs = 0.0)
    }

    private fun importDataset(dataset: S57Dataset, sourceImport: S57ImportResult?, decodeMs: Double): S57EngineImportResult {
        val indexStart = monotonicNowMs()
        val report = indexStore.importDataset(dataset)
        val indexMs = monotonicNowMs() - indexStart
        return S57EngineImportResult(
            cell = dataset.summary,
            indexReport = report,
            stats = indexStore.stats(),
            sourceImport = sourceImport,
            timing = EngineTimingReport(decodeMs = decodeMs, indexMs = indexMs)
        )
    }

    fun listCells(): List<S57CellSummary> = indexStore.listCells()

    fun stats(): S57IndexStats = indexStore.stats()

    fun clear() = indexStore.clear()

    fun render(request: ChartRenderRequest): S57EngineRenderResult {
        val frameStart = monotonicNowMs()
        val frame = staticRenderer.prepareFrame(request)
        val frameMs = monotonicNowMs() - frameStart
        val analyzeStart = monotonicNowMs()
        val diagnostics = analyzeRenderedArtifact(frame)
        val analyzeMs = monotonicNowMs() - analyzeStart
        return S57EngineRenderResult(
            frame = frame,
            diagnostics = diagnostics,
            timing = EngineTimingReport(framePrepareMs = frameMs, artifactAnalyzeMs = analyzeMs)
        )
    }

    fun centerCrosshairHits(request: ChartRenderRequest): List<ChartHitResult> {
        val withCrosshair = request.copy(centerCrosshair = request.centerCrosshair.copy(enabled = true, queryOnRender = true))
        return render(withCrosshair).frame.centerCrosshairHits
    }

    fun hitTest(request: ChartRenderRequest, screenPoint: ScreenPoint, radiusPx: Double = 12.0): List<ChartHitResult> =
        render(request).frame.hitTester().hitTest(screenPoint, radiusPx)
}

data class S57EngineImportResult(
    val cell: S57CellSummary,
    val indexReport: S57IndexImportReport,
    val stats: S57IndexStats,
    val sourceImport: S57ImportResult? = null,
    val timing: EngineTimingReport = EngineTimingReport()
) {
    fun toPlainText(): String = buildString {
        append("engineImport cell=${cell.cellId} features=${indexReport.featureCount} indexed=${indexReport.indexedFeatureCount} cells=${stats.cellCount} totalFeatures=${stats.featureCount}")
        sourceImport?.let { append(" rawVectors=${it.raw.vectors.size} geometryDiagnostics=${it.geometryDiagnosticCount}") }
        append(" ")
        append(timing.toPlainText("importTiming"))
    }
}

data class S57EngineRenderResult(
    val frame: StaticChartFrame,
    val diagnostics: RenderedArtifactReport,
    val timing: EngineTimingReport = EngineTimingReport()
) {
    fun toSvgSnapshot(includeLabels: Boolean = false): String = renderedArtifactSvgSnapshot(frame, includeLabels)
    fun validateMinimum(minVisibleFeatures: Int = 1) = diagnostics.validateMinimum(minVisibleFeatures = minVisibleFeatures)
}

fun chartRenderRequestForCell(
    cell: S57CellSummary,
    widthPx: Int = 1024,
    heightPx: Int = 768,
    scaleDenominator: Double? = null,
    center: GeoPoint? = null,
    paddingFraction: Double = 0.08
): ChartRenderRequest {
    val sourceBounds = requireNotNull(cell.bounds) { "Cell ${cell.cellId} has no bounds" }
    val viewport = ScreenSize(widthPx, heightPx)
    val fit = chartViewportFitForBounds(sourceBounds, viewport, paddingFraction)
    val cameraCenter = center ?: fit.cameraCenter
    val scale = scaleDenominator ?: fit.scaleDenominator
    return ChartRenderRequest(
        cellId = cell.cellId,
        bounds = fit.fittedBounds,
        widthPx = widthPx,
        heightPx = heightPx,
        scaleDenominator = scale,
        camera = ChartCameraState(
            center = cameraCenter,
            zoom = scale,
            viewport = viewport
        )
    )
}
