package io.github.s57.render

import java.io.File
import kotlin.system.exitProcess

/**
 * Phase 21 JVM smoke harness for a real NOAA ENC .000 file.
 *
 * Usage:
 *   gradle :s57-render-webgl:jvmRun -PmainClass=io.github.s57.render.NoaaEncVisualSmokeMainKt --args="/path/to/US5xxxxx.000 build/phase21/smoke.svg"
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("Usage: NoaaEncVisualSmokeMain <ENC .000 path> [snapshot.svg]")
        exitProcess(2)
    }

    val encFile = File(args[0])
    require(encFile.isFile) { "ENC file not found: ${encFile.absolutePath}" }
    val svgFile = args.getOrNull(1)?.let(::File)

    val engine = S57WebGlEngine()
    val imported = engine.importS57Bytes(encFile.readBytes())
    val request = chartRenderRequestForCell(imported.cell, widthPx = 1280, heightPx = 800).copy(
        centerCrosshair = CenterCrosshairConfig(enabled = true, queryOnRender = true),
        renderMode = ChartRenderMode.Flat2D
    )
    val rendered = engine.render(request)
    val smoke = noaaEncVisualSmokeReport(imported, rendered)

    println(smoke.toPlainText())

    svgFile?.let { target ->
        target.parentFile?.mkdirs()
        target.writeText(rendered.toSvgSnapshot(includeLabels = true))
        println("snapshot=${target.absolutePath}")
    }

    smoke.validate(
        requireRawDecode = true,
        minDecodedFeatures = 1,
        minIndexedFeatures = 1,
        minQueriedFeatures = 1,
        minAdaptedFeatures = 1,
        minProjectedFeatures = 1,
        minOnscreenFeatures = 1,
        minObjectClasses = 1
    )
}
