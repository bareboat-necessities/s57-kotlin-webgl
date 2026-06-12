package io.github.s57.render

import io.github.s52.core.draw.S52DrawCommand
import io.github.s52.core.geometry.EncGeometry
import io.github.s52.preslib.PresLibPack
import io.github.s52.render.webgl.RenderViewport

internal data class BrowserS52DisplayCommandPlan(
    val commands: List<S52DrawCommand>,
    val originalCommandCount: Int,
    val suppressedRasterAreaPatternCount: Int,
    val suppressedDuplicatePointSymbolCount: Int
) {
    val changed: Boolean get() = suppressedRasterAreaPatternCount > 0 || suppressedDuplicatePointSymbolCount > 0

    fun diagnostics(cellId: String): List<RenderPipelineDiagnostic> {
        if (!changed) return emptyList()
        return listOf(
            RenderPipelineDiagnostic(
                stage = RenderPipelineStage.S52Portrayal,
                severity = RenderPipelineSeverity.Info,
                code = "s52.strict_single_webgl_display_plan",
                message = "Strict S-52 WebGL-only display plan: original=$originalCommandCount webGlCommands=${commands.size} suppressedRasterAreaPatterns=$suppressedRasterAreaPatternCount suppressedDuplicatePointSymbols=$suppressedDuplicatePointSymbolCount",
                source = RenderPipelineSource(cellId = cellId),
                metadata = mapOf(
                    "originalCommands" to originalCommandCount.toString(),
                    "renderedCommands" to commands.size.toString(),
                    "suppressedRasterAreaPatterns" to suppressedRasterAreaPatternCount.toString(),
                    "suppressedDuplicatePointSymbols" to suppressedDuplicatePointSymbolCount.toString()
                )
            )
        )
    }
}

@Suppress("UNUSED_PARAMETER")
internal fun buildBrowserS52DisplayCommandPlan(
    commands: List<S52DrawCommand>,
    presLib: PresLibPack,
    viewport: RenderViewport,
    widthPx: Int,
    heightPx: Int,
    scaleDenominator: Double
): BrowserS52DisplayCommandPlan {
    val bestPointSymbols = selectBestPointSymbolByFeature(commands)
    val emittedPointSymbolFeatureIds = mutableSetOf<Long>()
    val webGlCommands = ArrayList<S52DrawCommand>(commands.size)
    var suppressedDuplicatePointSymbols = 0

    for (command in commands) {
        when (command) {
            is S52DrawCommand.PointSymbol -> {
                val best = bestPointSymbols[command.featureId]
                if (best != null && best == command && emittedPointSymbolFeatureIds.add(command.featureId)) {
                    webGlCommands += command
                } else {
                    suppressedDuplicatePointSymbols++
                }
            }
            else -> webGlCommands += command
        }
    }

    return BrowserS52DisplayCommandPlan(
        commands = webGlCommands,
        originalCommandCount = commands.size,
        suppressedRasterAreaPatternCount = 0,
        suppressedDuplicatePointSymbolCount = suppressedDuplicatePointSymbols
    )
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
