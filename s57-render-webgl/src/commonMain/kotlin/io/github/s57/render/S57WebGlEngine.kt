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
        val imported = importPipeline.importBytes(bytes)
        return importDataset(imported.dataset, imported)
    }

    fun importDataset(dataset: S57Dataset): S57EngineImportResult {
        return importDataset(dataset, null)
    }

    private fun importDataset(dataset: S57Dataset, sourceImport: S57ImportResult?): S57EngineImportResult {
        val report = indexStore.importDataset(dataset)
        return S57EngineImportResult(
            cell = dataset.summary,
            indexReport = report,
            stats = indexStore.stats(),
            sourceImport = sourceImport
        )
    }

    fun listCells(): List<S57CellSummary> = indexStore.listCells()

    fun stats(): S57IndexStats = indexStore.stats()

    fun clear() = indexStore.clear()

    fun render(request: ChartRenderRequest): S57EngineRenderResult {
        val frame = staticRenderer.prepareFrame(request)
        val diagnostics = RenderedArtifactDiagnostics.analyze(frame)
        return S57EngineRenderResult(frame, diagnostics)
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
    val sourceImport: S57ImportResult? = null
) {
    fun toPlainText(): String = buildString {
        append("engineImport cell=${cell.cellId} features=${indexReport.featureCount} indexed=${indexReport.indexedFeatureCount} cells=${stats.cellCount} totalFeatures=${stats.featureCount}")
        sourceImport?.let { append(" rawVectors=${it.raw.vectors.size} geometryDiagnostics=${it.geometryDiagnosticCount}") }
    }
}

data class S57EngineRenderResult(
    val frame: StaticChartFrame,
    val diagnostics: RenderedArtifactReport
) {
    fun toSvgSnapshot(includeLabels: Boolean = false): String = RenderedArtifactDiagnostics.toSvgSnapshot(frame, includeLabels)
    fun validateMinimum(minVisibleFeatures: Int = 1) = diagnostics.validateMinimum(minVisibleFeatures = minVisibleFeatures)
}

fun chartRenderRequestForCell(
    cell: S57CellSummary,
    widthPx: Int = 1024,
    heightPx: Int = 768,
    scaleDenominator: Double = 22_000.0,
    center: GeoPoint? = null
): ChartRenderRequest {
    val bounds = requireNotNull(cell.bounds) { "Cell ${cell.cellId} has no bounds" }
    val cameraCenter = center ?: GeoPoint((bounds.minLon + bounds.maxLon) / 2.0, (bounds.minLat + bounds.maxLat) / 2.0)
    return ChartRenderRequest(
        cellId = cell.cellId,
        bounds = bounds,
        widthPx = widthPx,
        heightPx = heightPx,
        scaleDenominator = scaleDenominator,
        camera = ChartCameraState(
            center = cameraCenter,
            zoom = scaleDenominator,
            viewport = ScreenSize(widthPx, heightPx)
        )
    )
}
