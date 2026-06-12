package io.github.s57.render

import io.github.s52.core.draw.S52DrawCommand
import io.github.s52.core.geometry.EncGeometry
import io.github.s52.preslib.PresLibPack
import io.github.s52.render.webgl.RenderViewport
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Value

internal data class BrowserS52DisplayCommandPlan(
    val commands: List<S52DrawCommand>,
    val textCommands: List<S52DrawCommand>,
    val originalCommandCount: Int,
    val suppressedRasterAreaPatternCount: Int,
    val suppressedDuplicatePointSymbolCount: Int,
    val reorderedAreaFillCount: Int
) {
    val changed: Boolean get() = suppressedRasterAreaPatternCount > 0 ||
        suppressedDuplicatePointSymbolCount > 0 ||
        reorderedAreaFillCount > 0 ||
        textCommands.isNotEmpty()

    fun diagnostics(cellId: String): List<RenderPipelineDiagnostic> {
        if (!changed) return emptyList()
        return listOf(
            RenderPipelineDiagnostic(
                stage = RenderPipelineStage.S52Portrayal,
                severity = RenderPipelineSeverity.Info,
                code = "s52.strict_single_webgl_display_plan",
                message = "Strict S-52 WebGL-only display plan: original=$originalCommandCount webGlCommands=${commands.size} webGlTextCommands=${textCommands.size} suppressedRasterAreaPatterns=$suppressedRasterAreaPatternCount suppressedDuplicatePointSymbols=$suppressedDuplicatePointSymbolCount reorderedAreaFills=$reorderedAreaFillCount",
                source = RenderPipelineSource(cellId = cellId),
                metadata = mapOf(
                    "originalCommands" to originalCommandCount.toString(),
                    "renderedCommands" to commands.size.toString(),
                    "webGlTextCommands" to textCommands.size.toString(),
                    "suppressedRasterAreaPatterns" to suppressedRasterAreaPatternCount.toString(),
                    "suppressedDuplicatePointSymbols" to suppressedDuplicatePointSymbolCount.toString(),
                    "reorderedAreaFills" to reorderedAreaFillCount.toString()
                )
            )
        )
    }
}

@Suppress("UNUSED_PARAMETER")
internal fun buildBrowserS52DisplayCommandPlan(
    commands: List<S52DrawCommand>,
    sourceFeatures: List<S57Feature>,
    presLib: PresLibPack,
    viewport: RenderViewport,
    widthPx: Int,
    heightPx: Int,
    scaleDenominator: Double
): BrowserS52DisplayCommandPlan {
    val objectClassByFeatureId = sourceFeatures.associate { it.id to it.objectClass.uppercase() }
    val featureById = sourceFeatures.associateBy { it.id }
    val bestPointSymbols = selectBestPointSymbolByFeature(commands)
    val emittedPointSymbolFeatureIds = mutableSetOf<Long>()
    val filtered = ArrayList<S52DrawCommand>(commands.size)
    val textCommands = ArrayList<S52DrawCommand>()
    var suppressedDuplicatePointSymbols = 0
    var suppressedRasterAreaPatterns = 0

    for (command in commands) {
        when (command) {
            is S52DrawCommand.PointSymbol -> {
                val best = bestPointSymbols[command.featureId]
                if (best != null && best == command && emittedPointSymbolFeatureIds.add(command.featureId)) {
                    filtered += command
                } else {
                    suppressedDuplicatePointSymbols++
                }
            }
            is S52DrawCommand.Text -> command.resolveDisplayLabel(featureById)?.let { textCommands += it }
            is S52DrawCommand.Sounding -> textCommands += command
            is S52DrawCommand.AreaPattern -> {
                val vectorLinePattern = command.withBrowserVectorLinePatternFallback(presLib)
                if (vectorLinePattern != command) suppressedRasterAreaPatterns++
                filtered += vectorLinePattern
            }
            else -> filtered += command
        }
    }

    val landBacked = filtered.withMissingLandBackedAreaFills(objectClassByFeatureId)
    val ordered = landBacked.sortedWith(StrictBrowserS52PainterOrder(objectClassByFeatureId))
    val reorderedAreaFills = countReorderedAreaFills(filtered, ordered)
    return BrowserS52DisplayCommandPlan(
        commands = ordered,
        textCommands = textCommands,
        originalCommandCount = commands.size,
        suppressedRasterAreaPatternCount = suppressedRasterAreaPatterns,
        suppressedDuplicatePointSymbolCount = suppressedDuplicatePointSymbols,
        reorderedAreaFillCount = reorderedAreaFills
    )
}


private fun S52DrawCommand.AreaPattern.withBrowserVectorLinePatternFallback(presLib: PresLibPack): S52DrawCommand.AreaPattern {
    val pattern = presLib.patterns.find(patternName) ?: return this
    if (pattern.bitmap == null) return this

    // The browser renderer in S-52 0.5.4 prefers OpenCPN raster pattern tiles
    // over HPGL/vector pattern strokes.  In NOAA snapshots those raster tiles are
    // visible as repeated dirty-orange rounded sprite boxes near shore.  Force the
    // renderer down its line-pattern fallback path until upstream can choose the
    // vector pattern before the raster atlas for area fills.
    val lineColorToken = pattern.colorRefs.firstOrNull() ?: backgroundColorToken ?: "CHMGD"
    return copy(
        patternName = BrowserVectorLinePatternFallbackPrefix + patternName,
        backgroundColorToken = lineColorToken
    )
}

private fun List<S52DrawCommand>.withMissingLandBackedAreaFills(objectClassByFeatureId: Map<Long, String>): List<S52DrawCommand> {
    val filledFeatureIds = asSequence()
        .filterIsInstance<S52DrawCommand.AreaFill>()
        .map { it.featureId.normalizedSourceFeatureId() }
        .toSet()
    val syntheticFills = ArrayList<S52DrawCommand.AreaFill>()
    for (command in this) {
        val featureId = command.featureId.normalizedSourceFeatureId()
        if (featureId in filledFeatureIds || syntheticFills.any { it.featureId.normalizedSourceFeatureId() == featureId }) continue
        val objectClass = objectClassByFeatureId[command.featureId] ?: objectClassByFeatureId[featureId] ?: continue
        if (!objectClass.isLandBackedAreaClass()) continue
        if (command.geometry !is EncGeometry.Polygon) continue
        syntheticFills += S52DrawCommand.AreaFill(
            featureId = command.featureId,
            geometry = command.geometry,
            colorToken = "LANDA",
            priority = command.priority,
            viewingGroup = command.viewingGroup,
            category = command.category,
            overRadar = command.overRadar
        )
    }
    if (syntheticFills.isEmpty()) return this
    return syntheticFills + this
}

private fun Long.normalizedSourceFeatureId(): Long = if (this >= 1000L && this % 1000L != 0L) this / 1000L else this

private fun String.isLandBackedAreaClass(): Boolean = uppercase() in LandBackedAreaClasses

private const val BrowserVectorLinePatternFallbackPrefix: String = "__S57_VECTOR_LINE__"

private val LandBackedAreaClasses = setOf(
    "BUAARE",
    "BUISGL",
    "LNDARE",
    "LNDRGN"
)


private fun S52DrawCommand.Text.resolveDisplayLabel(featureById: Map<Long, S57Feature>): S52DrawCommand.Text? {
    val feature = featureById[featureId] ?: featureById[splitSourceFeatureId()]
        ?: return if (textExpression.isUnresolvedTextPlaceholder()) null else this
    val label = resolveS57TextLabel(feature, textExpression, rawArgs) ?: return null
    return if (label == textExpression) this else copy(textExpression = label)
}

private fun S52DrawCommand.splitSourceFeatureId(): Long =
    if (featureId >= 1000L && featureId % 1000L != 0L) featureId / 1000L else featureId

private fun resolveS57TextLabel(feature: S57Feature, textExpression: String, rawArgs: List<String>): String? {
    val candidates = listOf(textExpression) + rawArgs
    for ((index, candidate) in candidates.withIndex()) {
        val resolved = resolveS57TextExpression(feature, candidate, candidates.getOrNull(index + 1))
        if (!resolved.isNullOrBlank() && !resolved.isUnresolvedTextPlaceholder()) return resolved
    }
    if (feature.objectClass.equals("LIGHTS", ignoreCase = true)) {
        return feature.lightDescription().takeIf { it.isNotBlank() }
    }
    return null
}

private fun resolveS57TextExpression(feature: S57Feature, expression: String, nextArgument: String?): String? {
    val trimmed = expression.trim().trim('"', '\'')
    if (trimmed.isBlank()) return null

    if (trimmed.contains('%')) {
        val attributeValue = nextArgument?.let { feature.attributeLabel(it) } ?: return null
        return trimmed.replaceFirst(PrintfTokenRegex, attributeValue).takeIf { it != trimmed }
    }

    feature.attributeLabel(trimmed)?.let { return it }

    if (trimmed.equals("LIGHTS", ignoreCase = true) || (feature.objectClass.equals("LIGHTS", ignoreCase = true) && trimmed.isLightAttributeToken())) {
        return feature.lightDescription().takeIf { it.isNotBlank() }
    }

    var replaced = trimmed
    var changed = false
    for (token in AttributeTokenRegex.findAll(trimmed.uppercase()).map { it.value }.distinct()) {
        val value = feature.attributeLabel(token) ?: continue
        replaced = replaced
            .replace("{$token}", value)
            .replace("[$token]", value)
            .replace("$" + token, value)
        changed = true
    }
    if (changed && replaced != trimmed) return replaced

    return trimmed.takeUnless { it.isUnresolvedTextPlaceholder() || it.isKnownAttributeAcronym() }
}

private fun S57Feature.lightDescription(): String {
    val pieces = mutableListOf<String>()
    attributeLabel("OBJNAM")?.let { pieces += it }
    attributeLabel("LITCHR")?.let { pieces += lightCharacteristicLabel(it) }
    attributeLabel("COLOUR")?.let { pieces += lightColorLabel(it) }
    attributeLabel("SIGGRP")?.let { pieces += it }
    attributeLabel("SIGPER")?.let { pieces += if (it.endsWith("s", ignoreCase = true)) it else it + "s" }
    val sector1 = attributeLabel("SECTR1")
    val sector2 = attributeLabel("SECTR2")
    if (sector1 != null && sector2 != null) pieces += "$sector1-$sector2°"
    return pieces.joinToString(" ")
}

private fun S57Feature.attributeLabel(acronymOrExpression: String): String? {
    val acronym = acronymOrExpression.trim().trim('"', '\'', '[', ']', '{', '}').uppercase()
    if (!acronym.isKnownAttributeAcronym()) return null
    return attributes[acronym]?.labelText()?.takeIf { it.isNotBlank() }
}

private fun S57Value.labelText(): String = when (this) {
    S57Value.Empty -> ""
    is S57Value.Text -> value.trim()
    is S57Value.Integer -> value.toString()
    is S57Value.Decimal -> value.formatLabelNumber()
    is S57Value.ListValue -> values.joinToString(",") { it.labelText() }.trim()
}

private fun Double.formatLabelNumber(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString().trimEnd('0').trimEnd('.')

private fun lightCharacteristicLabel(value: String): String = when (value.trim().toIntOrNull()) {
    1 -> "F"
    2 -> "Fl"
    3 -> "LFl"
    4 -> "Q"
    5 -> "VQ"
    6 -> "UQ"
    7 -> "Iso"
    8 -> "Oc"
    9 -> "IQ"
    10 -> "IVQ"
    11 -> "IUQ"
    12 -> "Mo"
    13 -> "F.Fl"
    14 -> "Fl.LFl"
    18 -> "LFl"
    19 -> "Fl"
    25 -> "Q"
    26 -> "VQ"
    27 -> "UQ"
    28 -> "Al"
    else -> "LITCHR=$value"
}

private fun lightColorLabel(value: String): String = value
    .split(',', ';', '|')
    .map { token ->
        when (token.trim().toIntOrNull()) {
            1 -> "W"
            3 -> "R"
            4 -> "G"
            5 -> "Bu"
            6 -> "Y"
            7 -> "Gy"
            8 -> "Br"
            9 -> "Am"
            11 -> "Or"
            12 -> "Ma"
            13 -> "Pk"
            else -> token.trim()
        }
    }
    .filter { it.isNotBlank() }
    .joinToString("")

private fun String.isLightAttributeToken(): Boolean = uppercase() in setOf("LIGHTS", "LITCHR", "COLOUR", "SIGGRP", "SIGPER", "SECTR1", "SECTR2")

private fun String.isUnresolvedTextPlaceholder(): Boolean {
    val value = trim().trim('"', '\'')
    return value == "%s" || value.matches(PrintfTokenRegex)
}

private fun String.isKnownAttributeAcronym(): Boolean = uppercase() in KnownTextAttributeAcronyms

private val PrintfTokenRegex = Regex("%[-+ #0,(]*\\d*(?:\\.\\d+)?(?:ll|l)?[A-Za-z]")
private val AttributeTokenRegex = Regex("[A-Z][A-Z0-9_]{2,9}")
private val KnownTextAttributeAcronyms = setOf(
    "OBJNAM", "NOBJNM", "INFORM", "NINFOM", "TXTDSC",
    "LIGHTS", "LITCHR", "COLOUR", "SIGGRP", "SIGPER", "SECTR1", "SECTR2",
    "HEIGHT", "CURVEL", "VALSOU", "VALDCO"
)

private class StrictBrowserS52PainterOrder(
    private val objectClassByFeatureId: Map<Long, String>
) : Comparator<S52DrawCommand> {
    override fun compare(a: S52DrawCommand, b: S52DrawCommand): Int = compareValuesBy(
        a,
        b,
        { it.strictLayer() },
        { it.strictAreaFillLayer() },
        { it.priority },
        { it.kind.order },
        { it.viewingGroup },
        { it.featureId },
        { if (it.overRadar) 1 else 0 }
    )

    private fun S52DrawCommand.strictLayer(): Int = when (this) {
        is S52DrawCommand.AreaFill -> 10
        is S52DrawCommand.AreaPattern -> 20
        is S52DrawCommand.LineSimple,
        is S52DrawCommand.LineComplex -> 30
        is S52DrawCommand.PointSymbol -> 40
        is S52DrawCommand.Text,
        is S52DrawCommand.Sounding -> 50
    }

    private fun S52DrawCommand.strictAreaFillLayer(): Int {
        if (this !is S52DrawCommand.AreaFill) return 0
        val objectClass = objectClassByFeatureId[featureId] ?: objectClassByFeatureId[featureId / 1000L]
        val color = colorToken.uppercase()
        return when {
            objectClass == "SEAARE" || color == "NODTA" -> 0
            objectClass == "DEPARE" || objectClass == "DRGARE" || color.startsWith("DEP") -> 1
            objectClass == "LAKARE" || objectClass == "RIVERS" || objectClass == "CANALS" -> 2
            objectClass == "LNDARE" || color == "LANDA" || color == "CSTLN" -> 90
            else -> 50
        }
    }
}

private fun countReorderedAreaFills(before: List<S52DrawCommand>, after: List<S52DrawCommand>): Int {
    val beforeFills = before.filterIsInstance<S52DrawCommand.AreaFill>()
    val afterFills = after.filterIsInstance<S52DrawCommand.AreaFill>()
    return beforeFills.indices.count { index -> beforeFills.getOrNull(index) != afterFills.getOrNull(index) }
}

private fun selectBestPointSymbolByFeature(commands: List<S52DrawCommand>): Map<Long, S52DrawCommand.PointSymbol> {
    val selected = linkedMapOf<Long, S52DrawCommand.PointSymbol>()
    for (command in commands) {
        if (command !is S52DrawCommand.PointSymbol) continue
        val current = selected[command.featureId]
        if (current == null || command.strictSymbolScore() > current.strictSymbolScore()) {
            selected[command.featureId] = command
        }
    }
    return selected
}

private fun S52DrawCommand.PointSymbol.strictSymbolScore(): Int {
    val name = symbolName.uppercase()
    var score = priority * 1000 + viewingGroup
    if (name.looksLikeFallbackSymbol()) score -= 1_000_000
    if (geometry is EncGeometry.Point) score += 50
    return score
}

private fun String.looksLikeFallbackSymbol(): Boolean = contains("FALLBACK") ||
    contains("UNKNOWN") ||
    contains("QUES") ||
    contains("DEBUG") ||
    contains("PLAIN") ||
    contains("GENERIC")
