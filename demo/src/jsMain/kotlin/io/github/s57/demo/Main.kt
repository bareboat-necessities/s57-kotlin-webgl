package io.github.s57.demo

import io.github.s57.core.GeoBounds
import io.github.s57.core.GeoPoint
import io.github.s57.core.S57CellSummary
import io.github.s57.core.S57Dataset
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Geometry
import io.github.s57.core.S57Value
import io.github.s57.index.InMemoryS57IndexStore
import io.github.s57.render.BrowserChartInput
import io.github.s57.render.BrowserS57WebGlRenderer
import io.github.s57.render.CenterCrosshairConfig
import io.github.s57.render.ChartCameraState
import io.github.s57.render.ChartInteractionController
import io.github.s57.render.ChartInteractionListener
import io.github.s57.render.ChartRenderMode
import io.github.s57.render.ChartRenderRequest
import io.github.s57.render.ChartUserEvent
import io.github.s57.render.DepthMeshConfig
import io.github.s57.render.S57StaticChartRenderer
import io.github.s57.render.ScreenSize
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLInputElement

fun main() {
    val fileInput = document.getElementById("fileInput") as HTMLInputElement
    val renderButton = document.getElementById("renderButton") as HTMLButtonElement
    val fileList = document.getElementById("fileList")
    val status = document.getElementById("status")
    val events = document.getElementById("events")

    val listener = object : ChartInteractionListener {
        override fun onUserEvent(event: ChartUserEvent) {
            events?.textContent = event.toString()
        }
    }
    BrowserChartInput(ChartInteractionController(listener = listener)).attach("chartCanvas")

    val store = InMemoryS57IndexStore().also { it.importDataset(sampleDataset()) }
    val staticRenderer = S57StaticChartRenderer(store)
    val browserRenderer = BrowserS57WebGlRenderer()

    fileInput.onchange = {
        val files = fileInput.files
        if (files == null || files.length == 0) {
            fileList?.textContent = "No files selected."
        } else {
            val lines = mutableListOf<String>()
            for (index in 0 until files.length) {
                val file = files.item(index) ?: continue
                lines += "${file.name} — ${file.size.toLong()} bytes"
            }
            fileList?.textContent = lines.joinToString("\n")
            status?.textContent = "Selected ${lines.size} file(s). The demo can render the built-in sample with real S-52 symbology; ENC import plumbing is available through S57WebGlEngine.importS57Bytes()."
        }
        null
    }

    renderButton.onclick = {
        val request = ChartRenderRequest(
            cellId = "PHASE7-DEMO",
            bounds = GeoBounds(-75.0, 39.0, -73.0, 41.0),
            widthPx = 1280,
            heightPx = 720,
            scaleDenominator = 40000.0,
            camera = ChartCameraState(
                center = GeoPoint(-74.0, 40.0),
                zoom = 40000.0,
                rotationDegrees = 12.0,
                tiltDegrees = 18.0,
                viewport = ScreenSize(1280, 720)
            ),
            centerCrosshair = CenterCrosshairConfig(enabled = true, queryOnRender = true),
            depthMesh = DepthMeshConfig(enabled = true, verticalExaggeration = 1.6),
            renderMode = ChartRenderMode.Tilted2D
        )
        val frame = staticRenderer.prepareFrame(request)
        val summary = browserRenderer.renderS52Frame("chartCanvas", frame)
        status?.textContent = summary.message + " (${summary.widthPx}x${summary.heightPx}); centerHits=${summary.centerCrosshairHits.size}"
        null
    }
}

private fun sampleDataset(): S57Dataset = S57Dataset(
    summary = S57CellSummary(
        cellId = "PHASE7-DEMO",
        name = "PHASE7-DEMO",
        bounds = GeoBounds(-75.0, 39.0, -73.0, 41.0),
        featureCount = 4
    ),
    features = listOf(
        S57Feature(
            id = 1,
            objectClass = "DEPARE",
            attributes = mapOf("DRVAL1" to S57Value.Decimal(0.0), "DRVAL2" to S57Value.Decimal(10.0)),
            geometry = S57Geometry.Polygon(
                listOf(
                    listOf(
                        GeoPoint(-74.8, 39.2),
                        GeoPoint(-73.2, 39.2),
                        GeoPoint(-73.2, 40.8),
                        GeoPoint(-74.8, 40.8),
                        GeoPoint(-74.8, 39.2)
                    )
                )
            )
        ),
        S57Feature(
            id = 2,
            objectClass = "DEPCNT",
            geometry = S57Geometry.LineString(listOf(GeoPoint(-74.8, 40.0), GeoPoint(-73.2, 40.0)))
        ),
        S57Feature(
            id = 3,
            objectClass = "SOUNDG",
            attributes = mapOf("VALSOU" to S57Value.Decimal(4.2)),
            geometry = S57Geometry.MultiPoint(listOf(GeoPoint(-74.1, 40.18), GeoPoint(-73.85, 40.28), GeoPoint(-74.0, 40.0)))
        ),
        S57Feature(
            id = 4,
            objectClass = "BOYLAT",
            geometry = S57Geometry.Point(GeoPoint(-73.65, 40.52))
        )
    )
)
