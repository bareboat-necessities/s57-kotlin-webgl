package io.github.s57.render

/** Phase 21 real-ENC visual smoke report. */
data class NoaaEncVisualSmokeReport(
    val cellId: String,
    val hasBounds: Boolean,
    val rawFeatureCount: Int,
    val rawVectorCount: Int,
    val decodedFeatureCount: Int,
    val geometryDiagnosticCount: Int,
    val indexedFeatureCount: Int,
    val queriedFeatureCount: Int,
    val adaptedFeatureCount: Int,
    val projectedFeatureCount: Int,
    val onscreenFeatureCount: Int,
    val offscreenFeatureCount: Int,
    val clippedFeatureCount: Int,
    val emptyGeometryCount: Int,
    val objectClassCounts: Map<String, Int>,
    val artifact: RenderedArtifactReport
) {
    val objectClassDiversity: Int get() = objectClassCounts.size

    fun validate(
        requireRawDecode: Boolean = false,
        minDecodedFeatures: Int = 1,
        minIndexedFeatures: Int = 1,
        minQueriedFeatures: Int = 1,
        minAdaptedFeatures: Int = 1,
        minProjectedFeatures: Int = 1,
        minOnscreenFeatures: Int = 1,
        minObjectClasses: Int = 1
    ) {
        require(hasBounds) { "Phase 21 failed: cell has no bounds" }
        if (requireRawDecode) {
            require(rawFeatureCount > 0) { "Phase 21 failed: raw feature count is zero" }
            require(rawVectorCount > 0) { "Phase 21 failed: raw vector count is zero" }
        }
        require(decodedFeatureCount >= minDecodedFeatures) { "Phase 21 failed: decodedFeatureCount=$decodedFeatureCount required=$minDecodedFeatures" }
        require(indexedFeatureCount >= minIndexedFeatures) { "Phase 21 failed: indexedFeatureCount=$indexedFeatureCount required=$minIndexedFeatures" }
        require(queriedFeatureCount >= minQueriedFeatures) { "Phase 21 failed: queriedFeatureCount=$queriedFeatureCount required=$minQueriedFeatures" }
        require(adaptedFeatureCount >= minAdaptedFeatures) { "Phase 21 failed: adaptedFeatureCount=$adaptedFeatureCount required=$minAdaptedFeatures" }
        require(projectedFeatureCount >= minProjectedFeatures) { "Phase 21 failed: projectedFeatureCount=$projectedFeatureCount required=$minProjectedFeatures" }
        require(onscreenFeatureCount >= minOnscreenFeatures) { "Phase 21 failed: onscreenFeatureCount=$onscreenFeatureCount offscreen=$offscreenFeatureCount clipped=$clippedFeatureCount required=$minOnscreenFeatures" }
        require(objectClassDiversity >= minObjectClasses) { "Phase 21 failed: objectClassDiversity=$objectClassDiversity required=$minObjectClasses classes=${objectClassCounts.keys.sorted()}" }
        artifact.validateMinimum(minVisibleFeatures = minOnscreenFeatures)
    }

    fun toPlainText(): String = buildString {
        appendLine("phase21 cell=$cellId hasBounds=$hasBounds")
        appendLine("rawFeatures=$rawFeatureCount rawVectors=$rawVectorCount decodedFeatures=$decodedFeatureCount geometryDiagnostics=$geometryDiagnosticCount")
        appendLine("indexedFeatures=$indexedFeatureCount queriedFeatures=$queriedFeatureCount adaptedFeatures=$adaptedFeatureCount projectedFeatures=$projectedFeatureCount")
        appendLine("onscreenFeatures=$onscreenFeatureCount offscreenFeatures=$offscreenFeatureCount clippedFeatures=$clippedFeatureCount emptyGeometry=$emptyGeometryCount")
        appendLine("objectClasses=" + objectClassCounts.entries.sortedByDescending { it.value }.joinToString(",") { it.key + "=" + it.value })
        appendLine(artifact.toPlainText())
    }
}

fun noaaEncVisualSmokeReport(
    importResult: S57EngineImportResult,
    renderResult: S57EngineRenderResult
): NoaaEncVisualSmokeReport {
    val sourceImport = importResult.sourceImport
    val datasetFeatures = sourceImport?.dataset?.features ?: renderResult.frame.projectedFeatures.mapNotNull { it.feature }
    return NoaaEncVisualSmokeReport(
        cellId = importResult.cell.cellId,
        hasBounds = importResult.cell.bounds != null,
        rawFeatureCount = sourceImport?.raw?.features?.size ?: 0,
        rawVectorCount = sourceImport?.raw?.vectors?.size ?: 0,
        decodedFeatureCount = sourceImport?.featureCount ?: importResult.indexReport.featureCount,
        geometryDiagnosticCount = sourceImport?.geometryDiagnosticCount ?: 0,
        indexedFeatureCount = importResult.indexReport.indexedFeatureCount,
        queriedFeatureCount = renderResult.frame.queriedFeatureCount,
        adaptedFeatureCount = renderResult.frame.adaptedFeatureCount,
        projectedFeatureCount = renderResult.frame.projectedFeatures.size,
        onscreenFeatureCount = renderResult.diagnostics.onscreenFeatureCount,
        offscreenFeatureCount = renderResult.diagnostics.offscreenFeatureCount,
        clippedFeatureCount = renderResult.diagnostics.clippedFeatureCount,
        emptyGeometryCount = renderResult.diagnostics.emptyGeometryCount,
        objectClassCounts = datasetFeatures.groupingBy { it.objectClass.uppercase() }.eachCount(),
        artifact = renderResult.diagnostics
    )
}
