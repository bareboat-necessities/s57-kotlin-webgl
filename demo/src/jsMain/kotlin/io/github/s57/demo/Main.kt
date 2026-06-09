package io.github.s57.demo

import io.github.s57.core.S57CellSummary
import io.github.s57.render.BrowserChartInput
import io.github.s57.render.BrowserS57FileImporter
import io.github.s57.render.BrowserS57WebGlRenderer
import io.github.s57.render.CenterCrosshairConfig
import io.github.s57.render.ChartInteractionController
import io.github.s57.render.ChartInteractionListener
import io.github.s57.render.ChartRenderMode
import io.github.s57.render.ChartUserEvent
import io.github.s57.render.DepthMeshConfig
import io.github.s57.render.Phase16Counters
import io.github.s57.render.S57EngineImportResult
import io.github.s57.render.S57WebGlEngine
import io.github.s57.render.chartRenderRequestForCell
import io.github.s57.render.renderS52FrameWithSummary
import io.github.s57.render.toPlainText
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File

fun main() {
    val fileInput = document.getElementById("fileInput") as HTMLInputElement
    val renderButton = document.getElementById("renderButton") as HTMLButtonElement
    val sampleButton = document.getElementById("sampleButton") as HTMLButtonElement
    val fileList = document.getElementById("fileList")
    val status = document.getElementById("status")
    val events = document.getElementById("events")
    val canvas = document.getElementById("chartCanvas") as HTMLCanvasElement

    BrowserChartInput(ChartInteractionController(listener = object : ChartInteractionListener {
        override fun onUserEvent(event: ChartUserEvent) {
            events?.textContent = event.toString()
        }
    })).attach("chartCanvas")

    val engine = S57WebGlEngine()
    val importer = BrowserS57FileImporter(engine)
    val renderer = BrowserS57WebGlRenderer()
    var imports = emptyList<S57EngineImportResult>()
    var failures = emptyList<String>()
    var activeCell: S57CellSummary? = null

    fun updateFileList(lines: List<String>) {
        fileList?.textContent = if (lines.isEmpty()) "No files selected." else lines.joinToString("\n")
    }

    fun selectActiveCell() {
        val cells = engine.listCells()
        activeCell = cells.firstOrNull { it.bounds != null } ?: cells.firstOrNull()
    }

    fun importSummary(): String = buildString {
        appendLine("S-57 import: imported=" + imports.size + " failed=" + failures.size)
        val cell = activeCell
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
            return
        }
        val request = chartRenderRequestForCell(cell, canvas.width, canvas.height, 40000.0).copy(
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
            emptyGeometry = result.diagnostics.emptyGeometryCount,
            adapterDiagnostics = result.frame.adapterDiagnostics.size,
            s52 = summary.s52
        )
        status?.textContent = buildString {
            appendLine("Rendered " + label + " cell=" + cell.cellId)
            appendLine("Phase16 diagnostics:")
            appendLine(counters.toPlainText())
            appendLine("S-52 message: " + summary.message)
            if (matchingImport != null) appendLine("index: " + matchingImport.indexReport.toPlainText())
            if (result.frame.adapterDiagnostics.isNotEmpty()) {
                appendLine("adapterDiagnostics:")
                result.frame.adapterDiagnostics.take(8).forEach { appendLine("- " + it) }
            }
            if (failures.isNotEmpty()) appendLine("importFailures=" + failures.size)
        }
    }

    fun loadSample() {
        engine.clear()
        failures = emptyList()
        imports = listOf(engine.importDataset(sampleDataset()))
        selectActiveCell()
        updateFileList(listOf("Built-in S-52 sanity sample"))
        activeCell?.let { renderCell(it, "sample") } ?: run { status?.textContent = "Sample import failed: no cell available." }
    }

    fun importFiles(files: List<File>, labels: List<String>) {
        engine.clear()
        imports = emptyList()
        failures = emptyList()
        activeCell = null
        updateFileList(labels)
        if (files.isEmpty()) {
            status?.textContent = "No files selected."
            return
        }
        fun next(index: Int) {
            if (index >= files.size) {
                selectActiveCell()
                status?.textContent = importSummary()
                return
            }
            val file = files[index]
            status?.textContent = "Importing " + file.name + " (" + (index + 1) + "/" + files.size + ")..."
            importer.importFile(file) { result ->
                val ok = result.getOrNull()
                if (ok == null) {
                    val err = result.exceptionOrNull()
                    failures = failures + (file.name + ": " + (err?.message ?: err.toString()))
                } else {
                    imports = imports + ok
                }
                updateFileList(labels + ("Imported=" + imports.size + " failed=" + failures.size))
                next(index + 1)
            }
        }
        next(0)
    }

    fileInput.onchange = {
        val files = fileInput.files
        if (files == null || files.length == 0) {
            updateFileList(emptyList())
            status?.textContent = "No files selected."
        } else {
            val selectedFiles = mutableListOf<File>()
            val labels = mutableListOf<String>()
            for (index in 0 until files.length) {
                val file = files.item(index) ?: continue
                selectedFiles += file
                labels += file.name + " — " + file.size.toLong() + " bytes"
            }
            importFiles(selectedFiles, labels)
        }
        null
    }

    renderButton.onclick = {
        activeCell?.let { renderCell(it, "selected ENC") } ?: run { status?.textContent = "No imported ENC cell is available. Select a .000 file first or use the sample button." }
        null
    }

    sampleButton.onclick = {
        loadSample()
        null
    }

    status?.textContent = "Phase 16C demo ready. Select an ENC .000 file to import, or render the built-in S-52 sample."
}
