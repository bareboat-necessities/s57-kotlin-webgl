package io.github.s57.demo

import io.github.s57.core.GeoBounds
import io.github.s57.core.GeoPoint
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
            status?.textContent = "Selected ${lines.size} file(s). ISO8211 parsing starts after the Phase 1 interaction/render contracts."
        }
        null
    }

    renderButton.onclick = {
        val request = ChartRenderRequest(
            cellId = "PHASE1-DEMO",
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
            renderMode = ChartRenderMode.DepthMesh3D
        )
        val summary = BrowserS57WebGlRenderer().renderStatic("chartCanvas", request)
        status?.textContent = summary.message + " (${summary.widthPx}x${summary.heightPx})"
        null
    }
}
