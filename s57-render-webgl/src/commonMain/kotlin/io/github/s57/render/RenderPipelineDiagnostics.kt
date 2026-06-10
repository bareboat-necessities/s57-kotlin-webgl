package io.github.s57.render

/**
 * Structured diagnostics that can travel from S-57 import through S-52
 * portrayal and WebGL drawing without reducing failures to free-form strings.
 */
data class RenderPipelineDiagnostic(
    val stage: RenderPipelineStage,
    val severity: RenderPipelineSeverity,
    val code: String,
    val message: String,
    val cellId: String? = null,
    val featureId: Long? = null,
    val recordId: String? = null,
    val objectClass: String? = null,
    val primitive: String? = null,
    val geometryType: String? = null,
    val attributes: List<String> = emptyList(),
    val s52Asset: String? = null,
    val colorToken: String? = null,
    val fallbackColor: String? = null
) {
    fun toPlainText(): String = buildString {
        append(severity.name.lowercase())
        append(" stage=").append(stage.id)
        append(" code=").append(code)
        cellId?.let { append(" cell=").append(it) }
        featureId?.let { append(" feature=").append(it) }
        recordId?.let { append(" record=").append(it) }
        objectClass?.let { append(" object=").append(it) }
        primitive?.let { append(" primitive=").append(it) }
        geometryType?.let { append(" geometry=").append(it) }
        if (attributes.isNotEmpty()) append(" attributes=").append(attributes.joinToString(","))
        s52Asset?.let { append(" s52Asset=").append(it) }
        colorToken?.let { append(" colorToken=").append(it) }
        fallbackColor?.let { append(" fallbackColor=").append(it) }
        append(" message=").append(message)
    }

    fun toJson(): String = buildString {
        append('{')
        appendJsonField("stage", stage.id)
        append(',')
        appendJsonField("severity", severity.name.lowercase())
        append(',')
        appendJsonField("code", code)
        append(',')
        appendJsonField("message", message)
        appendNullableJsonField("cellId", cellId)
        appendNullableJsonField("featureId", featureId)
        appendNullableJsonField("recordId", recordId)
        appendNullableJsonField("objectClass", objectClass)
        appendNullableJsonField("primitive", primitive)
        appendNullableJsonField("geometryType", geometryType)
        append(',')
        appendJsonArrayField("attributes", attributes)
        appendNullableJsonField("s52Asset", s52Asset)
        appendNullableJsonField("colorToken", colorToken)
        appendNullableJsonField("fallbackColor", fallbackColor)
        append('}')
    }
}

enum class RenderPipelineStage(val id: String) {
    Import("import"),
    Decode("decode"),
    Geometry("geometry"),
    Index("index"),
    Projection("projection"),
    Adapter("adapter"),
    S52Portrayal("s52-portrayal"),
    S52WebGl("s52-webgl"),
    WebGl("webgl"),
    Demo("demo"),
    CiSnapshot("ci-snapshot")
}

enum class RenderPipelineSeverity {
    Info,
    Warning,
    Error
}

data class RenderPipelineDiagnosticReport(
    val diagnostics: List<RenderPipelineDiagnostic>
) {
    val infoCount: Int get() = diagnostics.count { it.severity == RenderPipelineSeverity.Info }
    val warningCount: Int get() = diagnostics.count { it.severity == RenderPipelineSeverity.Warning }
    val errorCount: Int get() = diagnostics.count { it.severity == RenderPipelineSeverity.Error }

    fun countByStage(): Map<RenderPipelineStage, Int> = diagnostics.groupingBy { it.stage }.eachCount()
    fun countByCode(): Map<String, Int> = diagnostics.groupingBy { it.code }.eachCount()
    fun countByObjectClass(): Map<String, Int> = diagnostics.mapNotNull { it.objectClass }.groupingBy { it }.eachCount()

    fun toPlainText(): String = buildString {
        appendLine("renderPipelineDiagnostics total=${diagnostics.size} info=$infoCount warnings=$warningCount errors=$errorCount")
        if (diagnostics.isNotEmpty()) {
            appendLine("byStage=" + countByStage().entries.joinToString(",") { it.key.id + ":" + it.value })
            appendLine("byCode=" + countByCode().entries.joinToString(",") { it.key + ":" + it.value })
            diagnostics.forEach { appendLine("- " + it.toPlainText()) }
        }
    }.trimEnd()

    fun toJson(): String = buildString {
        append('{')
        appendJsonField("total", diagnostics.size)
        append(',')
        appendJsonField("info", infoCount)
        append(',')
        appendJsonField("warnings", warningCount)
        append(',')
        appendJsonField("errors", errorCount)
        append(',')
        append('"').append("byStage").append('"').append(':')
        appendStringIntMap(countByStage().mapKeys { it.key.id })
        append(',')
        append('"').append("byCode").append('"').append(':')
        appendStringIntMap(countByCode())
        append(',')
        append('"').append("byObjectClass").append('"').append(':')
        appendStringIntMap(countByObjectClass())
        append(',')
        append('"').append("diagnostics").append('"').append(':')
        append('[')
        diagnostics.forEachIndexed { index, diagnostic ->
            if (index > 0) append(',')
            append(diagnostic.toJson())
        }
        append(']')
        append('}')
    }
}

fun RenderedFrameSummary.pipelineDiagnosticReport(): RenderPipelineDiagnosticReport =
    RenderPipelineDiagnosticReport((pipelineDiagnostics + s52.diagnostics).distinct())


/** Exportable diagnostics bundle for demo downloads and CI snapshot artifacts. */
data class RenderSnapshotDiagnosticExport(
    val cellId: String,
    val paletteName: String,
    val scaleDenominator: Double,
    val widthPx: Int,
    val heightPx: Int,
    val renderMessage: String,
    val artifact: RenderedArtifactReport,
    val s52: S52RenderSummary,
    val pipeline: RenderPipelineDiagnosticReport,
    val importSummary: String? = null
) {
    fun toJson(): String = buildString {
        append('{')
        appendJsonField("cellId", cellId)
        append(',')
        appendJsonField("paletteName", paletteName)
        append(',')
        appendJsonField("scaleDenominator", scaleDenominator)
        append(',')
        appendJsonField("widthPx", widthPx)
        append(',')
        appendJsonField("heightPx", heightPx)
        append(',')
        appendJsonField("renderMessage", renderMessage)
        appendNullableJsonField("importSummary", importSummary)
        append(',')
        append('"').append("artifact").append('"').append(':').append(artifact.toJson())
        append(',')
        append('"').append("s52").append('"').append(':').append(s52.toJson())
        append(',')
        append('"').append("pipeline").append('"').append(':').append(pipeline.toJson())
        append('}')
    }
}

fun RenderedArtifactReport.toJson(): String = buildString {
    append('{')
    appendJsonField("widthPx", widthPx)
    append(',')
    appendJsonField("heightPx", heightPx)
    append(',')
    appendJsonField("featureCount", featureCount)
    append(',')
    appendJsonField("visibleFeatureCount", visibleFeatureCount)
    append(',')
    appendJsonField("onscreenFeatureCount", onscreenFeatureCount)
    append(',')
    appendJsonField("offscreenFeatureCount", offscreenFeatureCount)
    append(',')
    appendJsonField("clippedFeatureCount", clippedFeatureCount)
    append(',')
    appendJsonField("pointFeatureCount", pointFeatureCount)
    append(',')
    appendJsonField("lineFeatureCount", lineFeatureCount)
    append(',')
    appendJsonField("polygonFeatureCount", polygonFeatureCount)
    append(',')
    appendJsonField("emptyGeometryCount", emptyGeometryCount)
    append(',')
    appendJsonField("centerCrosshairHitCount", centerCrosshairHitCount)
    append(',')
    appendJsonField("depthMeshVertexCount", depthMeshVertexCount)
    append(',')
    appendJsonField("depthMeshTriangleCount", depthMeshTriangleCount)
    append(',')
    appendJsonField("fallbackPlaceholderCount", fallbackPlaceholderCount)
    append('}')
}

fun S52RenderSummary.toJson(): String = buildString {
    append('{')
    appendJsonField("profile", profile)
    append(',')
    appendJsonField("encFeatureCount", encFeatureCount)
    append(',')
    appendJsonField("commandCount", commandCount)
    append(',')
    appendJsonField("drawCallCount", drawCallCount)
    append(',')
    appendJsonField("areaCommandCount", areaCommandCount)
    append(',')
    appendJsonField("lineCommandCount", lineCommandCount)
    append(',')
    appendJsonField("symbolCommandCount", symbolCommandCount)
    append(',')
    appendJsonField("textCommandCount", textCommandCount)
    append(',')
    appendJsonField("soundingCommandCount", soundingCommandCount)
    append(',')
    appendJsonField("diagnosticCount", diagnosticCount)
    append(',')
    appendJsonField("unsupportedObjectClassCount", unsupportedObjectClassCount)
    append(',')
    appendJsonField("unsupportedAttributeCount", unsupportedAttributeCount)
    append(',')
    appendJsonField("missingSymbolCount", missingSymbolCount)
    append(',')
    appendJsonField("missingColorTokenCount", missingColorTokenCount)
    append(',')
    appendJsonField("fallbackColorCount", fallbackColorCount)
    append(',')
    appendJsonField("failureStage", failureStage)
    append('}')
}

private fun StringBuilder.appendNullableJsonField(name: String, value: String?) {
    if (value != null) {
        append(',')
        appendJsonField(name, value)
    }
}

private fun StringBuilder.appendNullableJsonField(name: String, value: Long?) {
    if (value != null) {
        append(',')
        appendJsonField(name, value)
    }
}

private fun StringBuilder.appendJsonField(name: String, value: String) {
    append('"').append(jsonEscape(name)).append('"').append(':')
    append('"').append(jsonEscape(value)).append('"')
}

private fun StringBuilder.appendJsonField(name: String, value: Int) {
    append('"').append(jsonEscape(name)).append('"').append(':').append(value)
}

private fun StringBuilder.appendJsonField(name: String, value: Double) {
    append('"').append(jsonEscape(name)).append('"').append(':').append(value)
}

private fun StringBuilder.appendJsonField(name: String, value: Long) {
    append('"').append(jsonEscape(name)).append('"').append(':').append(value)
}

private fun StringBuilder.appendJsonArrayField(name: String, values: List<String>) {
    append('"').append(jsonEscape(name)).append('"').append(':')
    append('[')
    values.forEachIndexed { index, value ->
        if (index > 0) append(',')
        append('"').append(jsonEscape(value)).append('"')
    }
    append(']')
}

private fun StringBuilder.appendStringIntMap(values: Map<String, Int>) {
    append('{')
    values.entries.sortedBy { it.key }.forEachIndexed { index, entry ->
        if (index > 0) append(',')
        appendJsonField(entry.key, entry.value)
    }
    append('}')
}

private fun jsonEscape(value: String): String = buildString {
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(ch)
        }
    }
}
