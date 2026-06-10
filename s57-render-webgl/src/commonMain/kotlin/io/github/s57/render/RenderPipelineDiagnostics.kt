package io.github.s57.render

/**
 * Structured diagnostics that can be threaded through S-57 import, indexing,
 * S-52 portrayal, and browser/WebGL drawing.
 *
 * The model intentionally avoids a JSON dependency so common tests, JVM smoke
 * tools, and Kotlin/JS browser code can export the same report shape.
 */
data class RenderPipelineDiagnostic(
    val stage: RenderPipelineStage,
    val severity: RenderPipelineSeverity,
    val code: String,
    val message: String,
    val source: RenderPipelineSource = RenderPipelineSource(),
    val metadata: Map<String, String> = emptyMap()
)

enum class RenderPipelineStage(val id: String) {
    S57RawDecode("s57-raw-decode"),
    S57FeatureDecode("s57-feature-decode"),
    S57Bounds("s57-bounds"),
    Geometry("geometry"),
    Index("index"),
    Query("query"),
    Adapter("adapter"),
    Projection("projection"),
    Viewport("viewport"),
    VisibleGeometry("visible-geometry"),
    S52Portrayal("s52-portrayal"),
    S52Asset("s52-asset"),
    S52Color("s52-color"),
    WebGl("webgl"),
    Artifact("artifact"),
    Unknown("unknown");

    companion object {
        fun fromId(id: String): RenderPipelineStage = entries.firstOrNull { it.id == id } ?: when (id) {
            "raw-decode" -> S57RawDecode
            "feature-decode" -> S57FeatureDecode
            "bounds" -> S57Bounds
            "portrayal" -> S52Portrayal
            "webgl2" -> WebGl
            "none" -> Unknown
            else -> Unknown
        }
    }
}

enum class RenderPipelineSeverity {
    Info,
    Warning,
    Error
}

data class RenderPipelineSource(
    val cellId: String? = null,
    val recordId: String? = null,
    val featureId: Long? = null,
    val objectClass: String? = null,
    val primitive: String? = null,
    val geometryType: String? = null,
    val attributes: Map<String, String> = emptyMap()
)

data class RenderPipelineDiagnosticReport(
    val diagnostics: List<RenderPipelineDiagnostic> = emptyList()
) {
    val errorCount: Int get() = diagnostics.count { it.severity == RenderPipelineSeverity.Error }
    val warningCount: Int get() = diagnostics.count { it.severity == RenderPipelineSeverity.Warning }
    val infoCount: Int get() = diagnostics.count { it.severity == RenderPipelineSeverity.Info }

    fun countsByStage(): Map<String, Int> = sortedCounts(diagnostics.groupingBy { it.stage.id }.eachCount())
    fun countsBySeverity(): Map<String, Int> = sortedCounts(diagnostics.groupingBy { it.severity.name.lowercase() }.eachCount())
    fun countsByObjectClass(): Map<String, Int> = sortedCounts(diagnostics.mapNotNull { it.source.objectClass }.groupingBy { it }.eachCount())
    fun countsByCode(): Map<String, Int> = sortedCounts(diagnostics.groupingBy { it.code }.eachCount())

    fun plus(other: RenderPipelineDiagnosticReport): RenderPipelineDiagnosticReport =
        RenderPipelineDiagnosticReport(diagnostics + other.diagnostics)

    fun toPlainText(): String = buildString {
        appendLine("renderPipelineDiagnostics total=${diagnostics.size} errors=$errorCount warnings=$warningCount infos=$infoCount")
        appendLine("stages=" + countsByStage().toSummaryText())
        appendLine("codes=" + countsByCode().toSummaryText())
        diagnostics.forEach { diagnostic ->
            append(diagnostic.severity.name.uppercase())
            append(' ')
            append(diagnostic.stage.id)
            append(' ')
            append(diagnostic.code)
            diagnostic.source.cellId?.let { append(" cell=").append(it) }
            diagnostic.source.featureId?.let { append(" feature=").append(it) }
            diagnostic.source.objectClass?.let { append(" object=").append(it) }
            append(" - ")
            appendLine(diagnostic.message)
        }
    }.trimEnd()

    fun toJson(): String = buildString {
        append('{')
        appendJsonField("total", diagnostics.size)
        append(',')
        appendJsonField("errors", errorCount)
        append(',')
        appendJsonField("warnings", warningCount)
        append(',')
        appendJsonField("infos", infoCount)
        append(',')
        appendJsonIntObjectField("countsByStage", countsByStage())
        append(',')
        appendJsonIntObjectField("countsBySeverity", countsBySeverity())
        append(',')
        appendJsonIntObjectField("countsByCode", countsByCode())
        append(',')
        appendJsonIntObjectField("countsByObjectClass", countsByObjectClass())
        append(',')
        append("\"diagnostics\":")
        append('[')
        diagnostics.forEachIndexed { index, diagnostic ->
            if (index > 0) append(',')
            appendDiagnosticJson(diagnostic)
        }
        append(']')
        append('}')
    }
}

fun Phase16Counters.toRenderPipelineDiagnostics(cellId: String? = null): RenderPipelineDiagnosticReport {
    val diagnostics = mutableListOf<RenderPipelineDiagnostic>()
    val currentStage = stage()
    if (currentStage != "none") {
        diagnostics += RenderPipelineDiagnostic(
            stage = RenderPipelineStage.fromId(currentStage),
            severity = RenderPipelineSeverity.Error,
            code = "pipeline-blocked",
            message = "Render pipeline is blocked at $currentStage",
            source = RenderPipelineSource(cellId = cellId),
            metadata = phase16Metadata()
        )
    }
    if (geometryDiagnostics > 0) {
        diagnostics += RenderPipelineDiagnostic(
            stage = RenderPipelineStage.Geometry,
            severity = RenderPipelineSeverity.Warning,
            code = "geometry-diagnostics-present",
            message = "Geometry reconstruction emitted $geometryDiagnostics diagnostics",
            source = RenderPipelineSource(cellId = cellId),
            metadata = phase16Metadata()
        )
    }
    if (adapterDiagnostics > 0) {
        diagnostics += RenderPipelineDiagnostic(
            stage = RenderPipelineStage.Adapter,
            severity = RenderPipelineSeverity.Warning,
            code = "adapter-diagnostics-present",
            message = "S-57 to S-52 adapter emitted $adapterDiagnostics diagnostics",
            source = RenderPipelineSource(cellId = cellId),
            metadata = phase16Metadata()
        )
    }
    if (s52.diagnosticCount > 0) {
        diagnostics += RenderPipelineDiagnostic(
            stage = RenderPipelineStage.S52Portrayal,
            severity = RenderPipelineSeverity.Warning,
            code = "s52-diagnostics-present",
            message = "S-52 portrayal emitted ${s52.diagnosticCount} diagnostics",
            source = RenderPipelineSource(cellId = cellId),
            metadata = phase16Metadata()
        )
    }
    if (s52.unsupportedObjectClassCount > 0) {
        diagnostics += RenderPipelineDiagnostic(
            stage = RenderPipelineStage.S52Portrayal,
            severity = RenderPipelineSeverity.Warning,
            code = "unsupported-object-classes",
            message = "S-52 portrayal reported ${s52.unsupportedObjectClassCount} unsupported object classes",
            source = RenderPipelineSource(cellId = cellId),
            metadata = phase16Metadata()
        )
    }
    if (s52.unsupportedAttributeCount > 0) {
        diagnostics += RenderPipelineDiagnostic(
            stage = RenderPipelineStage.S52Portrayal,
            severity = RenderPipelineSeverity.Warning,
            code = "unsupported-attributes",
            message = "S-52 portrayal reported ${s52.unsupportedAttributeCount} unsupported attributes",
            source = RenderPipelineSource(cellId = cellId),
            metadata = phase16Metadata()
        )
    }
    return RenderPipelineDiagnosticReport(diagnostics)
}

fun RenderedArtifactReport.toRenderPipelineDiagnostics(cellId: String? = null): RenderPipelineDiagnosticReport {
    val diagnostics = mutableListOf<RenderPipelineDiagnostic>()
    if (widthPx <= 0 || heightPx <= 0) {
        diagnostics += RenderPipelineDiagnostic(
            stage = RenderPipelineStage.Artifact,
            severity = RenderPipelineSeverity.Error,
            code = "invalid-artifact-dimensions",
            message = "Rendered artifact has invalid dimensions ${widthPx}x$heightPx",
            source = RenderPipelineSource(cellId = cellId),
            metadata = artifactMetadata()
        )
    }
    if (featureCount > 0 && onscreenFeatureCount == 0) {
        diagnostics += RenderPipelineDiagnostic(
            stage = RenderPipelineStage.Viewport,
            severity = RenderPipelineSeverity.Warning,
            code = "no-onscreen-features",
            message = "Rendered artifact has no onscreen features despite $featureCount projected features",
            source = RenderPipelineSource(cellId = cellId),
            metadata = artifactMetadata()
        )
    }
    if (emptyGeometryCount > 0) {
        diagnostics += RenderPipelineDiagnostic(
            stage = RenderPipelineStage.Geometry,
            severity = RenderPipelineSeverity.Warning,
            code = "empty-geometries",
            message = "Rendered artifact contains $emptyGeometryCount empty geometries",
            source = RenderPipelineSource(cellId = cellId),
            metadata = artifactMetadata()
        )
    }
    if (fallbackPlaceholderCount > 0) {
        diagnostics += RenderPipelineDiagnostic(
            stage = RenderPipelineStage.S52Asset,
            severity = RenderPipelineSeverity.Warning,
            code = "fallback-placeholders",
            message = "Rendered artifact contains $fallbackPlaceholderCount fallback placeholders",
            source = RenderPipelineSource(cellId = cellId),
            metadata = artifactMetadata()
        )
    }
    return RenderPipelineDiagnosticReport(diagnostics)
}

private fun Phase16Counters.phase16Metadata(): Map<String, String> = mapOf(
    "rawFeatures" to rawFeatures.toString(),
    "rawVectors" to rawVectors.toString(),
    "decodedFeatures" to decodedFeatures.toString(),
    "hasBounds" to hasBounds.toString(),
    "geometryDiagnostics" to geometryDiagnostics.toString(),
    "indexedFeatures" to indexedFeatures.toString(),
    "queriedFeatures" to queriedFeatures.toString(),
    "adaptedFeatures" to adaptedFeatures.toString(),
    "projectedFeatures" to projectedFeatures.toString(),
    "visibleFeatures" to visibleFeatures.toString(),
    "onscreenFeatures" to onscreenFeatures.toString(),
    "offscreenFeatures" to offscreenFeatures.toString(),
    "clippedFeatures" to clippedFeatures.toString(),
    "emptyGeometry" to emptyGeometry.toString(),
    "adapterDiagnostics" to adapterDiagnostics.toString(),
    "s52Profile" to s52.profile,
    "s52EncFeatures" to s52.encFeatureCount.toString(),
    "s52Commands" to s52.commandCount.toString(),
    "s52DrawCalls" to s52.drawCallCount.toString(),
    "s52Diagnostics" to s52.diagnosticCount.toString(),
    "unsupportedObjectClasses" to s52.unsupportedObjectClassCount.toString(),
    "unsupportedAttributes" to s52.unsupportedAttributeCount.toString(),
    "failureStage" to s52.failureStage
)

private fun RenderedArtifactReport.artifactMetadata(): Map<String, String> = mapOf(
    "widthPx" to widthPx.toString(),
    "heightPx" to heightPx.toString(),
    "featureCount" to featureCount.toString(),
    "visibleFeatureCount" to visibleFeatureCount.toString(),
    "onscreenFeatureCount" to onscreenFeatureCount.toString(),
    "offscreenFeatureCount" to offscreenFeatureCount.toString(),
    "clippedFeatureCount" to clippedFeatureCount.toString(),
    "pointFeatureCount" to pointFeatureCount.toString(),
    "lineFeatureCount" to lineFeatureCount.toString(),
    "polygonFeatureCount" to polygonFeatureCount.toString(),
    "emptyGeometryCount" to emptyGeometryCount.toString(),
    "centerCrosshairHitCount" to centerCrosshairHitCount.toString(),
    "depthMeshVertexCount" to depthMeshVertexCount.toString(),
    "depthMeshTriangleCount" to depthMeshTriangleCount.toString(),
    "fallbackPlaceholderCount" to fallbackPlaceholderCount.toString()
)

private fun sortedCounts(counts: Map<String, Int>): Map<String, Int> =
    counts.entries.sortedBy { it.key }.associate { it.key to it.value }

private fun Map<String, Int>.toSummaryText(): String =
    if (isEmpty()) "none" else entries.joinToString(",") { (key, value) -> "$key=$value" }

private fun StringBuilder.appendDiagnosticJson(diagnostic: RenderPipelineDiagnostic) {
    append('{')
    appendJsonField("stage", diagnostic.stage.id)
    append(',')
    appendJsonField("severity", diagnostic.severity.name.lowercase())
    append(',')
    appendJsonField("code", diagnostic.code)
    append(',')
    appendJsonField("message", diagnostic.message)
    append(',')
    append("\"source\":")
    appendSourceJson(diagnostic.source)
    append(',')
    appendJsonObjectField("metadata", diagnostic.metadata)
    append('}')
}

private fun StringBuilder.appendSourceJson(source: RenderPipelineSource) {
    append('{')
    var needsComma = false
    fun comma() {
        if (needsComma) append(',')
        needsComma = true
    }
    source.cellId?.let { comma(); appendJsonField("cellId", it) }
    source.recordId?.let { comma(); appendJsonField("recordId", it) }
    source.featureId?.let { comma(); appendJsonField("featureId", it) }
    source.objectClass?.let { comma(); appendJsonField("objectClass", it) }
    source.primitive?.let { comma(); appendJsonField("primitive", it) }
    source.geometryType?.let { comma(); appendJsonField("geometryType", it) }
    if (source.attributes.isNotEmpty()) {
        comma()
        appendJsonObjectField("attributes", source.attributes)
    }
    append('}')
}

private fun StringBuilder.appendJsonObjectField(name: String, values: Map<String, String>) {
    appendJsonString(name)
    append(':')
    appendJsonObject(values)
}

private fun StringBuilder.appendJsonIntObjectField(name: String, values: Map<String, Int>) {
    appendJsonString(name)
    append(':')
    append('{')
    values.entries.forEachIndexed { index, (key, value) ->
        if (index > 0) append(',')
        appendJsonString(key)
        append(':')
        append(value)
    }
    append('}')
}

private fun StringBuilder.appendJsonObject(values: Map<String, String>) {
    append('{')
    values.entries.forEachIndexed { index, (key, value) ->
        if (index > 0) append(',')
        appendJsonField(key, value)
    }
    append('}')
}

private fun StringBuilder.appendJsonField(name: String, value: String) {
    appendJsonString(name)
    append(':')
    appendJsonString(value)
}

private fun StringBuilder.appendJsonField(name: String, value: Int) {
    appendJsonString(name)
    append(':')
    append(value)
}

private fun StringBuilder.appendJsonField(name: String, value: Long) {
    appendJsonString(name)
    append(':')
    append(value)
}

private fun StringBuilder.appendJsonString(value: String) {
    append('"')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
    append('"')
}
