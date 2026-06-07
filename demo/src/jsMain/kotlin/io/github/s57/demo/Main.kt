package io.github.s57.demo

import io.github.s57.render.BrowserS57WebGlRenderer
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLInputElement

fun main() {
    val fileInput = document.getElementById("fileInput") as HTMLInputElement
    val renderButton = document.getElementById("renderButton") as HTMLButtonElement
    val fileList = document.getElementById("fileList")
    val status = document.getElementById("status")

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
            status?.textContent = "Selected ${lines.size} file(s). Parsing begins in Phase 1."
        }
        null
    }

    renderButton.onclick = {
        val summary = BrowserS57WebGlRenderer().renderPlaceholder("chartCanvas")
        status?.textContent = summary.message + " (${summary.widthPx}x${summary.heightPx})"
        null
    }
}
