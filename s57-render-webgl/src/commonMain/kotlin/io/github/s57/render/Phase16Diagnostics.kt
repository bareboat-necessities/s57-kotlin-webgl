package io.github.s57.render

data class Phase16Counters(
    val rawFeatures: Int = 0,
    val rawVectors: Int = 0,
    val decodedFeatures: Int = 0,
    val hasBounds: Boolean = false,
    val geometryDiagnostics: Int = 0,
    val indexedFeatures: Int = 0,
    val queriedFeatures: Int = 0,
    val adaptedFeatures: Int = 0,
    val projectedFeatures: Int = 0,
    val visibleFeatures: Int = 0,
    val onscreenFeatures: Int = visibleFeatures,
    val offscreenFeatures: Int = 0,
    val clippedFeatures: Int = 0,
    val emptyGeometry: Int = 0,
    val adapterDiagnostics: Int = 0,
    val s52: S52RenderSummary = S52RenderSummary()
) {
    fun stage(): String = when {
        rawFeatures == 0 && decodedFeatures == 0 -> "s57-raw-decode"
        !hasBounds -> "s57-bounds"
        decodedFeatures == 0 -> "s57-feature-decode"
        indexedFeatures == 0 -> "index"
        queriedFeatures == 0 -> "query"
        projectedFeatures == 0 -> "projection"
        onscreenFeatures == 0 && offscreenFeatures > 0 -> "viewport"
        visibleFeatures == 0 -> "visible-geometry"
        adaptedFeatures == 0 -> "adapter"
        s52.failureStage != "none" && !s52.usedGeometryFallback -> s52.failureStage
        !s52.hasCommands && !s52.usedGeometryFallback -> "s52-portrayal"
        else -> "none"
    }
}

fun Phase16Counters.lines(): List<String> = listOf(
    "stage=" + stage(),
    "rawFeatures=" + rawFeatures + " rawVectors=" + rawVectors,
    "decodedFeatures=" + decodedFeatures + " hasBounds=" + hasBounds + " geometryDiagnostics=" + geometryDiagnostics,
    "indexedFeatures=" + indexedFeatures + " queriedFeatures=" + queriedFeatures + " adaptedFeatures=" + adaptedFeatures + " projectedFeatures=" + projectedFeatures,
    "visibleFeatures=" + visibleFeatures + " onscreenFeatures=" + onscreenFeatures + " offscreenFeatures=" + offscreenFeatures + " clippedFeatures=" + clippedFeatures,
    "emptyGeometry=" + emptyGeometry + " adapterDiagnostics=" + adapterDiagnostics,
    "s52Profile=" + s52.profile + " s52EncFeatures=" + s52.encFeatureCount + " s52Commands=" + s52.commandCount + " s52RasterCommands=" + s52.rasterCommandCount + " s52DrawCalls=" + s52.drawCallCount,
    "s52Areas=" + s52.areaCommandCount + " s52Lines=" + s52.lineCommandCount + " s52Symbols=" + s52.symbolCommandCount + " s52Text=" + s52.textCommandCount + " s52Soundings=" + s52.soundingCommandCount
)

fun Phase16Counters.toPlainText(): String = lines().joinToString("\n")
