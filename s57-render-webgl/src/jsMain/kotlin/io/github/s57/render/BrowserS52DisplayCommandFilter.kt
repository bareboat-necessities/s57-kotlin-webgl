package io.github.s57.render

import io.github.s52.core.draw.S52DrawCommand
import io.github.s52.core.geometry.EncGeometry
import io.github.s52.preslib.PresLibPack
import io.github.s52.render.webgl.RenderViewport
import io.github.s57.core.S57Feature

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
    val bestPointSymbols = selectBestPointSymbolByFeature(commands)
    val emittedPointSymbolFeatureIds = mutableSetOf<Long>()
    val filtered = ArrayList<S52DrawCommand>(commands.size)
    val textCommands = ArrayList<S52DrawCommand>()
    var suppressedDuplicatePointSymbols = 0

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
            is S52DrawCommand.Text,
            is S52DrawCommand.Sounding -> textCommands += command
            else -> filtered += command
        }
    }

    val ordered = filtered.sortedWith(StrictBrowserS52PainterOrder(objectClassByFeatureId))
    val reorderedAreaFills = countReorderedAreaFills(filtered, ordered)
    return BrowserS52DisplayCommandPlan(
        commands = ordered,
        textCommands = textCommands,
        originalCommandCount = commands.size,
        suppressedRasterAreaPatternCount = 0,
        suppressedDuplicatePointSymbolCount = suppressedDuplicatePointSymbols,
        reorderedAreaFillCount = reorderedAreaFills
    )
}

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
