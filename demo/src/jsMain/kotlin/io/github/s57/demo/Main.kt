package io.github.s57.demo

import io.github.s57.core.GeoBounds
import io.github.s57.core.S57CellSummary
import io.github.s57.render.BrowserChartCacheEntry
import io.github.s57.render.BrowserChartIndexedDbCache
import io.github.s57.render.BrowserChartInput
import io.github.s57.render.BrowserS57FileImporter
import io.github.s57.render.BrowserS57WebGlRenderer
import io.github.s57.render.CachedChartPayload
import io.github.s57.render.CenterCrosshairConfig
import io.github.s57.render.ChartInteractionController
import io.github.s57.render.ChartInteractionListener
import io.github.s57.render.ChartRenderMode
import io.github.s57.render.ChartUserEvent
import io.github.s57.render.DepthMeshConfig
import io.github.s57.render.Phase16Counters
import io.github.s57.render.S57EngineImportResult
import io.github.s57.render.S57WebGlEngine
import io.github.s57.render.boundedScale
import io.github.s57.render.browserChartCacheSummary
import io.github.s57.render.chartRenderRequestForCell
import io.github.s57.render.chooseInitialActiveCell
import io.github.s57.render.normalizePaletteName
import io.github.s57.render.pipelineDiagnosticReport
import io.github.s57.render.renderS52FrameWithSummary
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
    sampleButton.parentElement?.appendChild(restoreCacheButton)
    sampleButton.parentElement?.appendChild(clearCacheButton)

    val cacheHeading = document.createElement("h3")
    cacheHeading.textContent = "Cached cells"
    val cacheList = document.createElement("pre") as HTMLPreElement
    cacheList.textContent = "Browser cache not loaded yet."
    status?.parentElement?.appendChild(cacheHeading)
    status?.parentElement?.appendChild(cacheList)

    BrowserChartInput(ChartInteractionController(listener = object : ChartInteractionListener {
        override fun onUserEvent(event: ChartUserEvent) {
            events?.textContent = event.toString()
        }
    })).attach("chartCanvas")

    val engine = S57WebGlEngine()
    val importer = BrowserS57FileImporter(engine)
    val renderer = BrowserS57WebGlRenderer()
    val chartCache = BrowserChartIndexedDbCache()
    var imports = emptyList<S57EngineImportResult>()
    var failures = emptyList<String>()
    var cachedEntries = emptyList<BrowserChartCacheEntry>()
    var activeCellId: String? = null
    var selectedFiles = emptyList<File>()
    var selectedLabels = emptyList<String>()
    var activeScaleOverride: Double? = null

    fun cells(): List<S57CellSummary> = engine.listCells()
    fun activeCell(): S57CellSummary? = activeCellId?.let { id -> cells().firstOrNull { it.cellId == id } }
        ?: cells().firstOrNull { it.bounds != null }
        ?: cells().firstOrNull()

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

    fun renderCell(cell: S57CellSummary, label: String) {
        if (cell.bounds == null) {
            status?.textContent = "Cannot render " + label + ": cell has no bounds.\n" + importSummary()
            updateCellSummary()
            return
        }
        val autoRequest = chartRenderRequestForCell(cell, canvas.width, canvas.height)
        val scale = boundedScale(activeScaleOverride ?: scaleInput.value.toDoubleOrNull() ?: autoRequest.scaleDenominator)
        activeScaleOverride = scale
        scaleInput.value = scale.roundToInt().toString()
        val palette = normalizePaletteName(paletteSelect.value)
        paletteSelect.value = palette
        val request = autoRequest.copy(
            scaleDenominator = scale,
            camera = autoRequest.camera.copy(zoom = scale),
            paletteName = palette,
            centerCrosshair = CenterCrosshairConfig(enabled = true, queryOnRender = true),
            depthMesh = DepthMeshConfig(enabled = false),
            renderMode = ChartRenderMode.Flat2D
        )
        val result = engine.render(request)
        val summary = renderer.renderS52FrameWithSummary("chartCanvas", result.frame)
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
        status?.textContent = buildString {
            appendLine("Rendered " + label + " cell=" + cell.cellId)
            appendLine("viewportFit bounds=" + request.bounds + " scale=" + request.scaleDenominator + " palette=" + request.paletteName)
            appendLine("Phase16 diagnostics:")
            appendLine(counters.toPlainText())
            if (pipelineDiagnostics.diagnostics.isNotEmpty()) {
                appendLine("Structured pipeline diagnostics:")
                appendLine(pipelineDiagnostics.toPlainText())
            }
            appendLine("S-52 message: " + summary.message)
            val pipelineReport = summary.pipelineDiagnosticReport()
            if (pipelineReport.diagnostics.isNotEmpty()) {
                appendLine("Render pipeline diagnostics:")
                appendLine(pipelineReport.toPlainText())
            }
            if (matchingImport != null) appendLine("index: " + matchingImport.indexReport.toPlainText())
            if (result.frame.adapterDiagnostics.isNotEmpty()) {
                appendLine("adapterDiagnostics:")
                result.frame.adapterDiagnostics.take(8).forEach { appendLine("- " + it) }
            }
            if (failures.isNotEmpty()) appendLine("importFailures=" + failures.size)
        }
        updateCellSummary()
    }

    fun renderActive(label: String = "active cell") {
        activeCell()?.let { renderCell(it, label) } ?: run {
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
        selectedFiles = emptyList()
        selectedLabels = emptyList()
        updateFileList()
        updateCellSummary()
        status?.textContent = "Cleared imported cells. Browser cache is unchanged."
    }

    fun cacheImportedPayload(fileName: String, bytes: ByteArray, imported: S57EngineImportResult, after: () -> Unit) {
        chartCache.put(fileName, bytes, imported) { result ->
            result.onFailure { failures = failures + (fileName + ": cache failed: " + (it.message ?: it.toString())) }
            refreshCacheList()
            after()
        }
    }

    fun importFiles(files: List<File>, labels: List<String>) {
        engine.clear()
        imports = emptyList()
        failures = emptyList()
        activeCellId = null
        activeScaleOverride = null
        scaleInput.value = ""
        selectedFiles = files
        selectedLabels = labels
        updateFileList()
        updateCellSummary()
        if (files.isEmpty()) {
            status?.textContent = "No files selected."
            return
        }
        fun next(index: Int) {
            if (index >= files.size) {
                activeCellId = chooseInitialActiveCell(cells(), activeCellId)
                updateCellSummary()
                if (imports.isNotEmpty()) {
                    renderActive("imported ENC")
                } else {
                    status?.textContent = importSummary()
                }
                return
            }
            val file = files[index]
            status?.textContent = "Importing " + file.name + " (" + (index + 1) + "/" + files.size + ")..."
            importer.readFileBytes(file) { bytesResult ->
                val bytes = bytesResult.getOrNull()
                if (bytes == null) {
                    val err = bytesResult.exceptionOrNull()
                    failures = failures + (file.name + ": " + (err?.message ?: err.toString()))
                    next(index + 1)
                } else {
                    try {
                        val imported = engine.importS57Bytes(bytes)
                        imports = imports + imported
                        cacheImportedPayload(file.name, bytes, imported) {
                            updateFileList(selectedLabels + ("Imported=" + imports.size + " failed=" + failures.size + " cached=" + cachedEntries.size))
                            updateCellSummary()
                            next(index + 1)
                        }
                    } catch (t: Throwable) {
                        failures = failures + (file.name + ": " + (t.message ?: t.toString()))
                        next(index + 1)
                    }
                }
            }
        }
        next(0)
    }

    fun restoreCachedPayloads(entries: List<BrowserChartCacheEntry>) {
        engine.clear()
        imports = emptyList()
        failures = emptyList()
        activeCellId = null
        activeScaleOverride = null
        scaleInput.value = ""
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
            status?.textContent = "Restoring cached " + entry.fileName + " (" + (index + 1) + "/" + entries.size + ")..."
            chartCache.load(entry.cacheKey) { loaded ->
                loaded.fold(
                    onSuccess = { payload: CachedChartPayload? ->
                        if (payload == null) {
                            failures = failures + (entry.fileName + ": cached payload missing")
                        } else {
                            try {
                                imports = imports + engine.importS57Bytes(payload.bytes)
                            } catch (t: Throwable) {
                                failures = failures + (entry.fileName + ": restore failed: " + (t.message ?: t.toString()))
                            }
                        }
                        next(index + 1)
                    },
                    onFailure = {
                        failures = failures + (entry.fileName + ": cache load failed: " + (it.message ?: it.toString()))
                        next(index + 1)
                    }
                )
            }
        }
        next(0)
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
                cacheImportedPayload("statue-liberty.000", buffer.toS57ByteArray(), imported) {
                    renderActive("bundled NOAA demo")
                }
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
        updateCellSummary()
        renderActive("palette change")
        null
    }

    scaleInput.onchange = {
        activeScaleOverride = scaleInput.value.toDoubleOrNull()?.let(::boundedScale)
        activeScaleOverride?.let { scaleInput.value = it.roundToInt().toString() }
        updateCellSummary()
        renderActive("scale change")
        null
    }

    zoomInButton.onclick = {
        val base = activeScaleOverride ?: scaleInput.value.toDoubleOrNull() ?: activeCell()?.let { chartRenderRequestForCell(it, canvas.width, canvas.height).scaleDenominator } ?: 40_000.0
        activeScaleOverride = boundedScale(base / 1.6)
        scaleInput.value = activeScaleOverride!!.roundToInt().toString()
        renderActive("zoom in")
        null
    }

    zoomOutButton.onclick = {
        val base = activeScaleOverride ?: scaleInput.value.toDoubleOrNull() ?: activeCell()?.let { chartRenderRequestForCell(it, canvas.width, canvas.height).scaleDenominator } ?: 40_000.0
        activeScaleOverride = boundedScale(base * 1.6)
        scaleInput.value = activeScaleOverride!!.roundToInt().toString()
        renderActive("zoom out")
        null
    }

    renderButton.onclick = {
        renderActive("active cell")
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
