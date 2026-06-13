package io.github.s57.demo

import io.github.s57.core.GeoBounds
import io.github.s57.core.S57CellSummary
import io.github.s57.render.BrowserChartCacheEntry
import io.github.s57.render.BrowserChartIndexedDbCache
import io.github.s57.render.BrowserChartInput
import io.github.s57.render.BrowserS57FileImporter
import io.github.s57.render.BrowserS57WebGlRenderer
import io.github.s57.render.ChartInteractionController
import io.github.s57.render.ChartInteractionListener
import io.github.s57.render.ChartUserEvent
import io.github.s57.render.ChartCanvasCommand
import io.github.s57.render.ChartCanvasEvent
import io.github.s57.render.ChartCanvasFrameRenderer
import io.github.s57.render.ScreenDelta
import io.github.s57.render.ScreenPoint
import io.github.s57.render.Phase16Counters
import io.github.s57.render.RenderPipelineDiagnosticReport
import io.github.s57.render.S57EngineImportResult
import io.github.s57.render.ScreenSize
import io.github.s57.render.S57ChartCanvas
import io.github.s57.render.S57WebGlEngine
import io.github.s57.render.boundedScale
import io.github.s57.render.browserChartCacheKey
import io.github.s57.render.browserChartCacheSummary
import io.github.s57.render.chartRenderRequestForCell
import io.github.s57.render.chartViewportFitForBounds
import io.github.s57.render.center
import io.github.s57.render.chooseInitialActiveCell
import io.github.s57.render.normalizePaletteName
import io.github.s57.render.pipelineDiagnosticReport
import io.github.s57.render.renderS52FrameWithSummary
import io.github.s57.render.renderS52FailureFrame
import io.github.s57.render.S52RenderSummary
import io.github.s57.render.toPlainText
import io.github.s57.render.toRenderPipelineDiagnostics
import io.github.s57.render.toS57ByteArray
import io.github.s57.render.viewerCellOptions
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLPreElement
import org.w3c.dom.HTMLSelectElement
import org.khronos.webgl.ArrayBuffer
import org.w3c.files.File
import kotlin.math.roundToInt

private fun setPhase26ReportGlobals(ready: Boolean, json: String) {
    js("""
        window.s57Phase26RenderReady = ready;
        window.s57Phase26LatestReportJson = json;
        window.s57Phase26ReportJson = json;
    """)
}

fun main() {
    val fileInput = document.getElementById("fileInput") as HTMLInputElement
    val renderButton = document.getElementById("renderButton") as HTMLButtonElement
    val reloadButton = document.getElementById("reloadButton") as HTMLButtonElement
    val clearButton = document.getElementById("clearButton") as HTMLButtonElement
    val zoomInButton = document.getElementById("zoomInButton") as HTMLButtonElement
    val zoomOutButton = document.getElementById("zoomOutButton") as HTMLButtonElement
    val sampleButton = document.getElementById("sampleButton") as HTMLButtonElement
    val cellSelect = document.getElementById("cellSelect") as HTMLSelectElement
    val paletteSelect = document.getElementById("paletteSelect") as HTMLSelectElement
    val scaleInput = document.getElementById("scaleInput") as HTMLInputElement
    val cellSummary = document.getElementById("cellSummary")
    val fileList = document.getElementById("fileList")
    val status = document.getElementById("status")
    val events = document.getElementById("events")
    val canvas = document.getElementById("chartCanvas") as HTMLCanvasElement

    val restoreCacheButton = document.createElement("button") as HTMLButtonElement
    restoreCacheButton.textContent = "Restore cached cells"
    val clearCacheButton = document.createElement("button") as HTMLButtonElement
    clearCacheButton.textContent = "Clear browser cache"
    val diagnosticsJsonButton = document.createElement("button") as HTMLButtonElement
    diagnosticsJsonButton.textContent = "Download diagnostics JSON"
    val canvasPngButton = document.createElement("button") as HTMLButtonElement
    canvasPngButton.textContent = "Download canvas PNG"
    sampleButton.parentElement?.appendChild(restoreCacheButton)
    sampleButton.parentElement?.appendChild(clearCacheButton)
    sampleButton.parentElement?.appendChild(diagnosticsJsonButton)
    sampleButton.parentElement?.appendChild(canvasPngButton)

    val cacheHeading = document.createElement("h3")
    cacheHeading.textContent = "Cached cells"
    val cacheList = document.createElement("pre") as HTMLPreElement
    cacheList.textContent = "Browser cache not loaded yet."
    status?.parentElement?.appendChild(cacheHeading)
    status?.parentElement?.appendChild(cacheList)

    val engine = S57WebGlEngine()
    val importer = BrowserS57FileImporter(engine)
    val renderer = BrowserS57WebGlRenderer()
    var latestCanvasRender: ChartCanvasEvent.ChartRendered? = null
    val chartCanvas = S57ChartCanvas(
        engine = engine,
        frameRenderer = ChartCanvasFrameRenderer { frame ->
            try {
                renderer.renderS52FrameWithSummary("chartCanvas", frame)
            } catch (t: Throwable) {
                renderer.renderS52FailureFrame(
                    canvasId = "chartCanvas",
                    frame = frame,
                    reason = "S-52 structured render threw: " + (t.message ?: t.toString()),
                    s52 = S52RenderSummary(failureStage = "webgl-render")
                )
            }
        },
        initialSize = ScreenSize(canvas.width, canvas.height),
        initialPaletteName = normalizePaletteName(paletteSelect.value)
    )
    var activeScaleOverride: Double? = null
    var scheduleInteractiveRender: (String) -> Unit = { reason ->
        chartCanvas.dispatch(ChartCanvasCommand.Render(reason))
    }
    chartCanvas.addListener { event ->
        when (event) {
            is ChartCanvasEvent.ChartRendered -> latestCanvasRender = event
            is ChartCanvasEvent.ObjectSelected -> events?.textContent = "selected " + event.hit.displayName + " at " + event.point
            is ChartCanvasEvent.Pressed -> events?.textContent = "pressed " + event.point + " hits=" + event.hits.size
            is ChartCanvasEvent.CursorMoved -> events?.textContent = "cursor=" + event.point + " geo=" + event.geoPoint
            is ChartCanvasEvent.CommandRejected -> events?.textContent = "canvas command rejected: " + event.reason
            is ChartCanvasEvent.StatusChanged -> Unit
        }
    }
    val chartInput = BrowserChartInput(
        controller = ChartInteractionController(listener = object : ChartInteractionListener {
        override fun onUserEvent(event: ChartUserEvent) {
            when (event) {
                is ChartUserEvent.ClickObject -> chartCanvas.dispatch(ChartCanvasCommand.Press(event.point))
                is ChartUserEvent.TouchObject -> chartCanvas.dispatch(ChartCanvasCommand.Press(event.point))
                is ChartUserEvent.Drag -> {
                    chartCanvas.dispatch(ChartCanvasCommand.Scroll(ScreenDelta(-event.delta.dx, -event.delta.dy), redraw = false))
                    scheduleInteractiveRender("drag")
                }
                is ChartUserEvent.Zoom -> {
                    chartCanvas.dispatch(ChartCanvasCommand.Zoom(event.factor, event.focus, redraw = false))
                    activeScaleOverride = chartCanvas.status().scaleDenominator
                    activeScaleOverride?.let { scaleInput.value = it.roundToInt().toString() }
                    scheduleInteractiveRender("zoom")
                }
                is ChartUserEvent.Scroll -> {
                    chartCanvas.dispatch(ChartCanvasCommand.Scroll(event.delta, redraw = false))
                    scheduleInteractiveRender("scroll")
                }
                else -> events?.textContent = event.toString()
            }
        }
    }),
        requireFocusForWheelZoom = true,
        wheelZoomOnly = true,
        invertWheelZoom = true
    )
    chartInput.attach("chartCanvas")
    window.addEventListener("s57-chart-canvas-replaced", { rawEvent ->
        val detail = rawEvent.asDynamic().detail
        if ((detail?.canvasId as? String) == "chartCanvas") chartInput.attach("chartCanvas")
    })
    val chartCache = BrowserChartIndexedDbCache()
    var imports = emptyList<S57EngineImportResult>()
    var failures = emptyList<String>()
    var cachedEntries = emptyList<BrowserChartCacheEntry>()
    var activeCellId: String? = null
    var selectedFiles = emptyList<File>()
    var selectedLabels = emptyList<String>()
    var importedObjectCacheEntries = emptyList<BrowserChartCacheEntry>()
    var latestPipelineReport = RenderPipelineDiagnosticReport(
        metadata = mapOf("status" to "Phase 26 viewer initialized; no render has completed yet")
    )

    fun publishPhase26Report(report: RenderPipelineDiagnosticReport) {
        latestPipelineReport = report
        val ready = report.diagnostics.isNotEmpty() || report.counters.isNotEmpty()
        val json = latestPipelineReport.toJson()
        setPhase26ReportGlobals(ready, json)
        // Keep the CI/report handoff as plain string/boolean properties.  Do
        // not expose Kotlin function objects here; Playwright reads
        // s57Phase26LatestReportJson directly.
    }

    fun downloadUrl(fileName: String, url: String) {
        val anchor = document.createElement("a")
        anchor.setAttribute("href", url)
        anchor.setAttribute("download", fileName)
        document.body?.appendChild(anchor)
        anchor.asDynamic().click()
        anchor.parentNode?.removeChild(anchor)
    }

    fun downloadTextFile(fileName: String, mimeType: String, text: String) {
        val blob = js("new Blob([text], { type: mimeType })")
        val url = js("URL.createObjectURL(blob)")
        downloadUrl(fileName, url.unsafeCast<String>())
        js("URL.revokeObjectURL(url)")
    }

    publishPhase26Report(latestPipelineReport)

    fun cells(): List<S57CellSummary> = engine.listCells()
    fun activeCell(): S57CellSummary? = activeCellId?.let { id -> cells().firstOrNull { it.cellId == id } }
        ?: cells().firstOrNull { it.bounds != null }
        ?: cells().firstOrNull()

    fun displayedChartIds(): List<String> = cells().filter { it.bounds != null }.map { it.cellId }
        .ifEmpty { activeCell()?.let { listOf(it.cellId) } ?: emptyList() }

    fun formatBounds(bounds: GeoBounds?): String = bounds?.let {
        "W=${it.minLon}, S=${it.minLat}, E=${it.maxLon}, N=${it.maxLat}"
    } ?: "none"

    fun updateCacheList(entries: List<BrowserChartCacheEntry> = cachedEntries) {
        cachedEntries = entries
        cacheList.textContent = browserChartCacheSummary(cachedEntries)
    }

    fun refreshCacheList() {
        chartCache.list { result ->
            result.fold(
                onSuccess = { updateCacheList(it) },
                onFailure = { cacheList.textContent = "Browser cache unavailable: " + (it.message ?: it.toString()) }
            )
        }
    }

    fun updateCellSelector() {
        val allCells = cells()
        cellSelect.innerHTML = ""
        if (allCells.isEmpty()) {
            val option = document.createElement("option")
            option.setAttribute("value", "")
            option.textContent = "No imported cells"
            cellSelect.appendChild(option)
            activeCellId = null
        } else {
            activeCellId = chooseInitialActiveCell(allCells, activeCellId)
            viewerCellOptions(allCells).forEach { cell ->
                val option = document.createElement("option")
                option.setAttribute("value", cell.cellId)
                if (cell.cellId == activeCellId) option.setAttribute("selected", "selected")
                option.textContent = cell.label
                cellSelect.appendChild(option)
            }
        }
    }

    fun updateFileList(lines: List<String> = selectedLabels) {
        fileList?.textContent = if (lines.isEmpty()) "No files selected." else lines.joinToString("\n")
    }

    fun updateCellSummary() {
        updateCellSelector()
        val cell = activeCell()
        val matchingImport = cell?.let { active -> imports.lastOrNull { it.cell.cellId == active.cellId } }
        cellSummary?.textContent = if (cell == null) {
            "No cell selected."
        } else {
            buildString {
                appendLine("cellId=" + cell.cellId)
                appendLine("name=" + cell.name)
                appendLine("features=" + cell.featureCount)
                appendLine("bounds=" + formatBounds(cell.bounds))
                matchingImport?.let { import ->
                    appendLine("indexed=" + import.indexReport.indexedFeatureCount)
                    import.sourceImport?.let { source ->
                        appendLine("rawFeatures=" + source.raw.features.size + " rawVectors=" + source.raw.vectors.size)
                        appendLine("decodedFeatures=" + source.featureCount + " geometryDiagnostics=" + source.geometryDiagnosticCount)
                    }
                }
                appendLine("palette=" + normalizePaletteName(paletteSelect.value))
                appendLine("scale=" + (activeScaleOverride?.roundToInt()?.toString() ?: "auto"))
            }
        }
    }

    var pendingInteractiveRenderTimer: Int? = null
    var pendingInteractiveRenderReason: String = "interactive"
    var lastInteractiveRenderAtMs: Double = 0.0
    val minInteractiveRenderIntervalMs = 85.0

    fun nowMs(): Double = js("(typeof performance !== 'undefined' && performance.now) ? performance.now() : Date.now()").unsafeCast<Double>()

    fun syncScaleInputFromCanvas() {
        val scale = chartCanvas.status().scaleDenominator
        activeScaleOverride = scale
        if (scale != null) scaleInput.value = scale.roundToInt().toString()
    }

    fun runInteractiveRender(reason: String) {
        latestCanvasRender = null
        chartCanvas.dispatch(ChartCanvasCommand.Render(reason))
        lastInteractiveRenderAtMs = nowMs()
        syncScaleInputFromCanvas()
        latestCanvasRender?.let { rendered ->
            status?.textContent = buildString {
                appendLine("Rendered interactive " + reason + " scale=" + (chartCanvas.status().scaleDenominator?.roundToInt()?.toString() ?: "auto"))
                appendLine(rendered.drawing.message)
                appendLine("Use Download diagnostics JSON for the last full diagnostic report.")
            }
        }
        updateCellSummary()
    }

    scheduleInteractiveRender = { reason ->
        pendingInteractiveRenderReason = reason
        if (pendingInteractiveRenderTimer == null) {
            val elapsed = nowMs() - lastInteractiveRenderAtMs
            val delay = (minInteractiveRenderIntervalMs - elapsed).coerceAtLeast(0.0).roundToInt()
            pendingInteractiveRenderTimer = window.setTimeout({
                val renderReason = pendingInteractiveRenderReason
                pendingInteractiveRenderTimer = null
                runInteractiveRender(renderReason)
            }, delay)
        }
    }

    fun importSummary(): String = buildString {
        appendLine("S-57 import: imported=" + imports.size + " failed=" + failures.size + " cells=" + cells().size + " cached=" + cachedEntries.size)
        val cell = activeCell()
        appendLine(if (cell == null) "activeCell=none" else "activeCell=" + cell.cellId + " bounds=" + (cell.bounds ?: "none") + " features=" + cell.featureCount)
        imports.forEachIndexed { index, result ->
            appendLine("[" + (index + 1) + "] " + result.toPlainText())
            result.sourceImport?.let { source ->
                appendLine("rawFeatures=" + source.raw.features.size + " rawVectors=" + source.raw.vectors.size + " decodedFeatures=" + source.featureCount + " geometryDiagnostics=" + source.geometryDiagnosticCount)
            }
        }
        if (failures.isNotEmpty()) {
            appendLine("Failures:")
            failures.forEach { appendLine("- " + it) }
        }
    }

    fun phase26SnapshotMode(): Boolean = js("window.s57Phase26SnapshotMode === true").unsafeCast<Boolean>()

    fun snapshotBounds(cell: S57CellSummary, fraction: Double): GeoBounds? {
        val bounds = cell.bounds ?: return null
        val clamped = fraction.coerceIn(0.05, 1.0)
        val center = bounds.center()
        val halfLon = (bounds.maxLon - bounds.minLon) * clamped / 2.0
        val halfLat = (bounds.maxLat - bounds.minLat) * clamped / 2.0
        return GeoBounds(
            minLon = center.lon - halfLon,
            minLat = center.lat - halfLat,
            maxLon = center.lon + halfLon,
            maxLat = center.lat + halfLat
        )
    }

    fun renderCell(cell: S57CellSummary, label: String, boundsFraction: Double? = null) {
        if (cell.bounds == null) {
            status?.textContent = "Cannot render " + label + ": cell has no bounds.\n" + importSummary()
            updateCellSummary()
            return
        }
        chartCanvas.dispatch(ChartCanvasCommand.Resize(ScreenSize(canvas.width, canvas.height)))
        val chartIds = displayedChartIds().let { ids -> listOf(cell.cellId) + ids.filterNot { it == cell.cellId } }
        val previousCanvasStatus = chartCanvas.status()
        val shouldFitView = boundsFraction != null ||
            previousCanvasStatus.activeChartId != cell.cellId ||
            previousCanvasStatus.displayedChartIds != chartIds ||
            previousCanvasStatus.bounds == null
        chartCanvas.dispatch(ChartCanvasCommand.ShowCharts(chartIds, fitToFirstChart = shouldFitView, redraw = false))
        val fullCellRequest = chartRenderRequestForCell(cell, canvas.width, canvas.height)
        val autoScale = boundsFraction?.let { fraction ->
            val bounds = snapshotBounds(cell, fraction) ?: fullCellRequest.bounds
            val fit = chartViewportFitForBounds(bounds, ScreenSize(canvas.width, canvas.height), paddingFraction = 0.08)
            chartCanvas.dispatch(ChartCanvasCommand.SetView(fit.fittedBounds, fit.scaleDenominator, redraw = false))
            fit.scaleDenominator
        } ?: chartCanvas.status().scaleDenominator ?: fullCellRequest.scaleDenominator
        val scale = boundedScale(
            if (boundsFraction != null) autoScale else activeScaleOverride ?: scaleInput.value.toDoubleOrNull() ?: autoScale
        )
        activeScaleOverride = scale
        scaleInput.value = scale.roundToInt().toString()
        val palette = normalizePaletteName(paletteSelect.value)
        paletteSelect.value = palette
        latestCanvasRender = null
        chartCanvas.dispatch(ChartCanvasCommand.SetPalette(palette, redraw = false))
        chartCanvas.dispatch(ChartCanvasCommand.SetScale(scale, redraw = false))
        chartCanvas.dispatch(ChartCanvasCommand.Render(label))
        val rendered = latestCanvasRender
        if (rendered == null) {
            status?.textContent = "Chart canvas did not render " + label + "."
            updateCellSummary()
            return
        }
        val result = rendered.result
        val summary = rendered.drawing
        val matchingImport = imports.lastOrNull { it.cell.cellId == cell.cellId }
        val source = matchingImport?.sourceImport
        val counters = Phase16Counters(
            rawFeatures = source?.raw?.features?.size ?: 0,
            rawVectors = source?.raw?.vectors?.size ?: 0,
            decodedFeatures = source?.featureCount ?: matchingImport?.indexReport?.featureCount ?: cell.featureCount,
            hasBounds = true,
            geometryDiagnostics = source?.geometryDiagnosticCount ?: 0,
            indexedFeatures = matchingImport?.indexReport?.indexedFeatureCount ?: 0,
            queriedFeatures = result.frame.queriedFeatureCount,
            adaptedFeatures = result.frame.adaptedFeatureCount,
            projectedFeatures = result.frame.projectedFeatures.size,
            visibleFeatures = result.diagnostics.visibleFeatureCount,
            onscreenFeatures = result.diagnostics.onscreenFeatureCount,
            offscreenFeatures = result.diagnostics.offscreenFeatureCount,
            clippedFeatures = result.diagnostics.clippedFeatureCount,
            emptyGeometry = result.diagnostics.emptyGeometryCount,
            adapterDiagnostics = result.frame.adapterDiagnostics.size,
            s52 = summary.s52
        )
        val pipelineDiagnostics = counters.toRenderPipelineDiagnostics(cell.cellId)
            .plus(result.diagnostics.toRenderPipelineDiagnostics(cell.cellId))
        val renderReport = summary.pipelineDiagnosticReport(cell.cellId, palette, scale)
        val canvasStatus = chartCanvas.status()
        val combinedReport = pipelineDiagnostics
            .plus(renderReport)
            .withContext(
                cellId = cell.cellId,
                palette = palette,
                scaleDenominator = scale,
                metadata = mapOf(
                    "label" to label,
                    "bounds" to (canvasStatus.bounds?.toString() ?: "none"),
                    "chartCanvas" to canvasStatus.toString()
                )
            )
        publishPhase26Report(combinedReport)
        status?.textContent = buildString {
            appendLine("Rendered " + label + " activeCell=" + cell.cellId + " quiltedCells=" + canvasStatus.displayedChartIds.size)
            appendLine("chartCanvas displayed=" + canvasStatus.displayedChartIds.joinToString(",") + " cursor=" + canvasStatus.cursor + " center=" + canvasStatus.center)
            appendLine("viewportFit bounds=" + canvasStatus.bounds + " scale=" + canvasStatus.scaleDenominator + " palette=" + canvasStatus.paletteName)
            appendLine("Phase16 diagnostics:")
            appendLine(counters.toPlainText())
            if (pipelineDiagnostics.diagnostics.isNotEmpty()) {
                appendLine("Structured pipeline diagnostics:")
                appendLine(pipelineDiagnostics.toPlainText())
            }
            appendLine("S-52 message: " + summary.message)
            if (renderReport.diagnostics.isNotEmpty()) {
                appendLine("Render pipeline diagnostics:")
                appendLine(renderReport.toPlainText())
            }
            appendLine("Diagnostics JSON is available through the download button and window.s57Phase26ReportJson().")
            if (matchingImport != null) appendLine("index: " + matchingImport.indexReport.toPlainText())
            if (result.frame.adapterDiagnostics.isNotEmpty()) {
                appendLine("adapterDiagnostics:")
                result.frame.adapterDiagnostics.take(8).forEach { appendLine("- " + it) }
            }
            if (failures.isNotEmpty()) appendLine("importFailures=" + failures.size)
        }
        updateCellSummary()
    }

    fun renderActive(label: String = "active cell", boundsFraction: Double? = null) {
        activeCell()?.let { renderCell(it, label, boundsFraction) } ?: run {
            status?.textContent = "No imported ENC cell is available. Select a .000 file first, restore cache, or use the sample button."
            updateCellSummary()
        }
    }

    fun loadSample() {
        engine.clear()
        failures = emptyList()
        imports = listOf(engine.importDataset(sampleDataset()))
        activeCellId = imports.firstOrNull()?.cell?.cellId
        selectedFiles = emptyList()
        selectedLabels = listOf("Built-in S-52 sanity sample")
        activeScaleOverride = null
        scaleInput.value = ""
        updateFileList()
        updateCellSummary()
        renderActive("sample")
    }

    fun clearAll() {
        engine.clear()
        imports = emptyList()
        failures = emptyList()
        activeCellId = null
        activeScaleOverride = null
        scaleInput.value = ""
        importedObjectCacheEntries = emptyList()
        selectedFiles = emptyList()
        selectedLabels = emptyList()
        updateFileList()
        updateCellSummary()
        status?.textContent = "Cleared imported cells. Browser cache is unchanged."
    }

    fun cacheImportedPayloadSequence(fileName: String, payloads: List<ByteArray>, imported: S57EngineImportResult) {
        chartCache.putSequence(fileName, payloads, imported) { result ->
            result.onFailure { failures = failures + (fileName + ": cache failed: " + (it.message ?: it.toString())) }
            updateFileList(selectedLabels + ("Imported=" + imports.size + " failed=" + failures.size + " cached=" + cachedEntries.size))
            refreshCacheList()
        }
    }

    fun cacheImportedPayload(fileName: String, bytes: ByteArray, imported: S57EngineImportResult) {
        cacheImportedPayloadSequence(fileName, listOf(bytes), imported)
    }

    fun restoreCachedPayloads(entries: List<BrowserChartCacheEntry>) {
        engine.clear()
        imports = emptyList()
        failures = emptyList()
        activeCellId = null
        activeScaleOverride = null
        scaleInput.value = ""
        importedObjectCacheEntries = emptyList()
        selectedFiles = emptyList()
        selectedLabels = entries.map { "cached: " + it.label() }
        updateFileList()
        updateCellSummary()
        if (entries.isEmpty()) {
            status?.textContent = "No cached cells to restore."
            return
        }
        fun next(index: Int) {
            if (index >= entries.size) {
                activeCellId = chooseInitialActiveCell(cells(), activeCellId)
                updateCellSummary()
                status?.textContent = "Restored cached cells.\n" + importSummary()
                renderActive("restored cache")
                return
            }
            val entry = entries[index]
            status?.textContent = "Restoring cached chart objects " + entry.fileName + " (" + (index + 1) + "/" + entries.size + ")..."
            chartCache.loadDataset(entry.cacheKey) { loaded ->
                loaded.fold(
                    onSuccess = { dataset ->
                        if (dataset == null) {
                            chartCache.load(entry.cacheKey) { payloadResult ->
                                payloadResult.fold(
                                    onSuccess = { cached ->
                                        if (cached == null) {
                                            failures = failures + (entry.fileName + ": cached dataset and payload bytes are missing; re-import this cell")
                                        } else {
                                            try {
                                                imports = imports + engine.importS57ByteSequence(cached.payloads)
                                            } catch (t: Throwable) {
                                                failures = failures + (entry.fileName + ": payload restore failed: " + (t.message ?: t.toString()))
                                            }
                                        }
                                        next(index + 1)
                                    },
                                    onFailure = {
                                        failures = failures + (entry.fileName + ": payload cache load failed: " + (it.message ?: it.toString()))
                                        next(index + 1)
                                    }
                                )
                            }
                        } else {
                            try {
                                imports = imports + engine.importDataset(dataset)
                            } catch (t: Throwable) {
                                failures = failures + (entry.fileName + ": object restore failed: " + (t.message ?: t.toString()))
                            }
                            next(index + 1)
                        }
                    },
                    onFailure = {
                        failures = failures + (entry.fileName + ": object cache load failed: " + (it.message ?: it.toString()))
                        next(index + 1)
                    }
                )
            }
        }
        next(0)
    }

    fun finishImport() {
        activeCellId = chooseInitialActiveCell(cells(), activeCellId)
        updateCellSummary()
        if (imports.isNotEmpty()) {
            if (phase26SnapshotMode()) {
                status?.textContent = "Imported ENC cells are ready for Phase 26 CI snapshot rendering.\n" + importSummary()
            } else {
                renderActive("imported ENC")
            }
        } else {
            status?.textContent = importSummary()
        }
    }

    fun importChartGroups(groups: List<BrowserNoaaChartGroup>) {
        val importable = groups.filter { it.base != null }
        groups.filter { it.base == null }.forEach { group ->
            failures = failures + (group.chartId + ": skipped updates without required .000 base cell")
        }
        if (importable.isEmpty()) {
            finishImport()
            return
        }

        fun importDecodedGroup(group: BrowserNoaaChartGroup, ordered: List<BrowserNoaaChartPayload>, index: Int, next: (Int) -> Unit) {
            status?.textContent = "Importing " + group.label + " (" + (index + 1) + "/" + importable.size + ")..."
            try {
                val imported = engine.importS57ByteSequence(ordered.map { it.bytes })
                imports = imports + imported
                updateFileList(selectedLabels + ("Imported=" + imports.size + " failed=" + failures.size + " cached=" + cachedEntries.size))
                updateCellSummary()
                chartCache.putSequence(group.label, ordered.map { it.bytes }, imported) { result ->
                    result.fold(
                        onSuccess = { entry -> importedObjectCacheEntries = importedObjectCacheEntries + entry },
                        onFailure = { failures = failures + (group.label + ": object cache failed: " + (it.message ?: it.toString())) }
                    )
                    refreshCacheList()
                    next(index + 1)
                }
            } catch (t: Throwable) {
                failures = failures + (group.label + ": " + (t.message ?: t.toString()))
                next(index + 1)
            }
        }

        fun importCachedDatasetIfPresent(group: BrowserNoaaChartGroup, ordered: List<BrowserNoaaChartPayload>, index: Int, next: (Int) -> Unit) {
            val byteCount = ordered.sumOf { it.bytes.size }
            val cacheKey = browserChartCacheKey(group.label, byteCount)
            status?.textContent = "Checking decoded IndexedDB cache for " + group.label + " (" + (index + 1) + "/" + importable.size + ")..."
            var settled = false
            val timeoutHandle = window.setTimeout({
                if (!settled) {
                    settled = true
                    failures = failures + (group.label + ": decoded IndexedDB cache lookup timed out, falling back to byte decode")
                    importDecodedGroup(group, ordered, index, next)
                }
            }, 1500)
            chartCache.loadDataset(cacheKey) { loaded ->
                if (settled) return@loadDataset
                settled = true
                window.clearTimeout(timeoutHandle)
                loaded.fold(
                    onSuccess = { dataset ->
                        if (dataset == null) {
                            importDecodedGroup(group, ordered, index, next)
                        } else {
                            try {
                                val imported = engine.importDataset(dataset)
                                imports = imports + imported
                                selectedLabels = selectedLabels + ("cache-hit: " + group.label)
                                updateFileList(selectedLabels + ("Imported=" + imports.size + " failed=" + failures.size + " cached=" + cachedEntries.size))
                                updateCellSummary()
                                next(index + 1)
                            } catch (t: Throwable) {
                                failures = failures + (group.label + ": decoded cache import failed, falling back to byte decode: " + (t.message ?: t.toString()))
                                importDecodedGroup(group, ordered, index, next)
                            }
                        }
                    },
                    onFailure = {
                        failures = failures + (group.label + ": decoded cache lookup failed, falling back to byte decode: " + (it.message ?: it.toString()))
                        importDecodedGroup(group, ordered, index, next)
                    }
                )
            }
        }

        fun next(index: Int) {
            if (index >= importable.size) {
                finishImport()
                return
            }
            val group = importable[index]
            val ordered = group.contiguousImportPayloads
            val missingUpdate = group.firstMissingUpdateNumber
            if (missingUpdate != null && missingUpdate <= group.importPayloads.last().updateNumber) {
                failures = failures + (group.chartId + ": stopped update chain before missing ." + missingUpdate.toString().padStart(3, '0'))
            }
            importCachedDatasetIfPresent(group, ordered, index, ::next)
        }
        next(0)
    }

    fun importFiles(files: List<File>, labels: List<String>) {
        engine.clear()
        imports = emptyList()
        failures = emptyList()
        activeCellId = null
        activeScaleOverride = null
        scaleInput.value = ""
        importedObjectCacheEntries = emptyList()
        selectedFiles = files
        selectedLabels = labels
        updateFileList()
        updateCellSummary()
        if (files.isEmpty()) {
            status?.textContent = "No files selected."
            return
        }
        val payloads = mutableListOf<BrowserNoaaChartPayload>()
        fun collect(index: Int) {
            if (index >= files.size) {
                val groups = groupNoaaChartPayloads(payloads)
                selectedLabels = labels + payloads.map { "chart: " + it.label }
                updateFileList()
                status?.textContent = "Discovered " + payloads.size + " ENC payload(s) in " + groups.size + " chart cell group(s)."
                importChartGroups(groups)
                return
            }
            val file = files[index]
            status?.textContent = "Reading " + file.name + " (" + (index + 1) + "/" + files.size + ")..."
            val isZip = file.name.lowercase().endsWith(".zip")
            if (isZip) {
                importer.readFileArrayBuffer(file) { bufferResult ->
                    bufferResult.fold(
                        onSuccess = { buffer ->
                            extractNoaaChartsFromZip(file, buffer) { zipResult ->
                                zipResult.fold(
                                    onSuccess = { extracted -> payloads += extracted },
                                    onFailure = { failures = failures + (file.name + ": " + (it.message ?: it.toString())) }
                                )
                                collect(index + 1)
                            }
                        },
                        onFailure = {
                            failures = failures + (file.name + ": " + (it.message ?: it.toString()))
                            collect(index + 1)
                        }
                    )
                }
            } else {
                val parsed = noaaChartPathOrNull(file.name)
                if (parsed == null) {
                    failures = failures + (file.name + ": skipped; expected .zip or NOAA ENC .000/.001/... file")
                    collect(index + 1)
                } else {
                    importer.readFileBytes(file) { bytesResult ->
                        bytesResult.fold(
                            onSuccess = { bytes ->
                                payloads += BrowserNoaaChartPayload(file.name, parsed.first, parsed.second, bytes)
                            },
                            onFailure = { failures = failures + (file.name + ": " + (it.message ?: it.toString())) }
                        )
                        collect(index + 1)
                    }
                }
            }
        }
        collect(0)
    }

    fun restoreCache() {
        chartCache.list { result ->
            result.fold(
                onSuccess = {
                    updateCacheList(it)
                    restoreCachedPayloads(it)
                },
                onFailure = { status?.textContent = "Cannot restore cache: " + (it.message ?: it.toString()) }
            )
        }
    }


    fun importBundledArrayBuffer(buffer: ArrayBuffer) {
        if (imports.isNotEmpty() || selectedFiles.isNotEmpty() || cells().isNotEmpty()) return
        engine.clear()
        imports = emptyList()
        failures = emptyList()
        activeCellId = null
        activeScaleOverride = null
        scaleInput.value = ""
        importedObjectCacheEntries = emptyList()
        selectedFiles = emptyList()
        selectedLabels = listOf("bundled: data/statue-liberty.000")
        updateFileList()
        updateCellSummary()
        importer.importArrayBuffer(buffer).fold(
            onSuccess = { imported ->
                imports = listOf(imported)
                activeCellId = imported.cell.cellId
                selectedLabels = listOf("bundled: data/statue-liberty.000")
                updateFileList()
                updateCellSummary()
                renderActive("bundled NOAA demo")
                cacheImportedPayload("statue-liberty.000", buffer.toS57ByteArray(), imported)
            },
            onFailure = { error ->
                failures = failures + ("data/statue-liberty.000: " + (error.message ?: error.toString()))
                status?.textContent = "Bundled NOAA demo import failed. Use the file picker to load a .000 cell.\n" + importSummary()
                updateCellSummary()
            }
        )
    }

    fun tryLoadBundledDemo() {
        val fetchFn = window.asDynamic().fetch
        if (fetchFn == null) return
        window.asDynamic().fetch("data/statue-liberty.000").then(
            { response: dynamic ->
                if (response != null && response.ok == true) {
                    response.arrayBuffer().then(
                        { buffer: dynamic ->
                            importBundledArrayBuffer(buffer.unsafeCast<ArrayBuffer>())
                            null
                        },
                        { _: dynamic -> null }
                    )
                }
                null
            },
            { _: dynamic -> null }
        )
    }

    js("""
        window.s57Phase26RenderSnapshot = function() {
            var button = document.querySelector('#renderButton');
            if (button) button.click();
            return null;
        };
        window.s57Phase26RenderSnapshotWithBoundsFraction = function(fraction, label) {
            if (typeof fraction === 'number' && isFinite(fraction)) {
                window.s57Phase26SnapshotBoundsFraction = fraction;
            }
            window.s57Phase26SnapshotLabel = label || '';
            var button = document.querySelector('#renderButton');
            if (button) button.click();
            return null;
        };
    """)

    fun selectedInputFiles(): List<File> {
        val files = fileInput.files ?: return emptyList()
        val out = mutableListOf<File>()
        for (index in 0 until files.length) {
            val file = files.item(index) ?: continue
            out += file
        }
        return out
    }

    fun selectedInputLabels(files: List<File>): List<String> = files.map { it.name + " — " + it.size.toLong() + " bytes" }

    fileInput.onchange = {
        val files = selectedInputFiles()
        importFiles(files, selectedInputLabels(files))
        null
    }

    cellSelect.onchange = {
        activeCellId = cellSelect.value.takeIf { it.isNotBlank() }
        activeScaleOverride = null
        scaleInput.value = ""
        updateCellSummary()
        renderActive("selected cell")
        null
    }

    paletteSelect.onchange = {
        paletteSelect.value = normalizePaletteName(paletteSelect.value)
        chartCanvas.dispatch(ChartCanvasCommand.SetPalette(paletteSelect.value, redraw = false))
        updateCellSummary()
        latestCanvasRender = null
        chartCanvas.dispatch(ChartCanvasCommand.Render("palette change"))
        syncScaleInputFromCanvas()
        null
    }

    scaleInput.onchange = {
        activeScaleOverride = scaleInput.value.toDoubleOrNull()?.let(::boundedScale)
        activeScaleOverride?.let { scaleInput.value = it.roundToInt().toString() }
        activeScaleOverride?.let { chartCanvas.dispatch(ChartCanvasCommand.SetScale(it, redraw = false)) }
        updateCellSummary()
        latestCanvasRender = null
        chartCanvas.dispatch(ChartCanvasCommand.Render("scale change"))
        syncScaleInputFromCanvas()
        null
    }

    zoomInButton.onclick = {
        val focus = ScreenPoint(canvas.width.toDouble() / 2.0, canvas.height.toDouble() / 2.0)
        chartCanvas.dispatch(ChartCanvasCommand.Zoom(1.6, focus, redraw = false))
        syncScaleInputFromCanvas()
        updateCellSummary()
        scheduleInteractiveRender("zoom in")
        null
    }

    zoomOutButton.onclick = {
        val focus = ScreenPoint(canvas.width.toDouble() / 2.0, canvas.height.toDouble() / 2.0)
        chartCanvas.dispatch(ChartCanvasCommand.Zoom(1.0 / 1.6, focus, redraw = false))
        syncScaleInputFromCanvas()
        updateCellSummary()
        scheduleInteractiveRender("zoom out")
        null
    }

    renderButton.onclick = {
        if (phase26SnapshotMode()) {
            val snapshotFraction = (window.asDynamic().s57Phase26SnapshotBoundsFraction as? Number)?.toDouble() ?: 0.35
            val snapshotLabel = (window.asDynamic().s57Phase26SnapshotLabel as? String)?.takeIf { it.isNotBlank() }
            renderActive("Phase 26 CI snapshot" + (snapshotLabel?.let { " " + it } ?: ""), boundsFraction = snapshotFraction)
        } else renderActive("active cell")
        null
    }

    reloadButton.onclick = {
        if (selectedFiles.isEmpty()) {
            status?.textContent = "No selected browser files to reload. Choose .000 files again or restore browser cache."
        } else {
            importFiles(selectedFiles, selectedLabels)
        }
        null
    }

    restoreCacheButton.onclick = {
        restoreCache()
        null
    }

    clearButton.onclick = {
        clearAll()
        null
    }

    diagnosticsJsonButton.onclick = {
        downloadTextFile(
            fileName = "s57-phase26-diagnostics.json",
            mimeType = "application/json",
            text = latestPipelineReport.toJson()
        )
        null
    }

    canvasPngButton.onclick = {
        downloadUrl("s57-phase26-render.png", canvas.toDataURL("image/png"))
        null
    }

    clearCacheButton.onclick = {
        chartCache.clear { result ->
            result.fold(
                onSuccess = {
                    updateCacheList(emptyList())
                    status?.textContent = "Browser cache cleared. Imported cells are unchanged."
                },
                onFailure = { status?.textContent = "Failed to clear browser cache: " + (it.message ?: it.toString()) }
            )
        }
        null
    }

    sampleButton.onclick = {
        loadSample()
        null
    }

    updateCellSummary()
    refreshCacheList()
    status?.textContent = "Phase 26 viewer ready. Import ENC files, restore browser cache, select a cell, choose palette, and adjust scale/zoom."
    tryLoadBundledDemo()
}
