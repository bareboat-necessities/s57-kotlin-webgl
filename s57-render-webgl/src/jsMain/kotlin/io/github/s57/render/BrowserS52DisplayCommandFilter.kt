package io.github.s57.render

import io.github.s52.core.draw.S52DrawCommand
import io.github.s52.core.geometry.Coordinate
import io.github.s52.core.geometry.EncGeometry
import io.github.s52.preslib.PresLibPack
import io.github.s52.render.webgl.RenderViewport
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class BrowserS52DisplayCommandPlan(
    val commands: List<S52DrawCommand>,
    val labelCommands: List<S52DrawCommand>,
    val originalCommandCount: Int,
    val injectedAreaFillCount: Int,
    val suppressedRasterAreaPatternCount: Int,
    val suppressedTextCount: Int,
    val suppressedSoundingCount: Int
) {
    val changed: Boolean get() = injectedAreaFillCount > 0 || suppressedRasterAreaPatternCount > 0 || suppressedTextCount > 0 || suppressedSoundingCount > 0

    fun diagnostics(cellId: String): List<RenderPipelineDiagnostic> {
        if (!changed) return emptyList()
        return listOf(
            RenderPipelineDiagnostic(
                stage = RenderPipelineStage.S52Portrayal,
                severity = RenderPipelineSeverity.Info,
                code = "s52.browser_display_filter",
                message = "Browser display filter prepared S-52 commands for interactive viewing: original=$originalCommandCount webGlCommands=${commands.size} vectorLabels=${labelCommands.size} injectedAreaFills=$injectedAreaFillCount suppressedRasterAreaPatterns=$suppressedRasterAreaPatternCount suppressedText=$suppressedTextCount suppressedSoundings=$suppressedSoundingCount",
                source = RenderPipelineSource(cellId = cellId),
                metadata = mapOf(
                    "originalCommands" to originalCommandCount.toString(),
                    "renderedCommands" to commands.size.toString(),
                    "vectorLabelCommands" to labelCommands.size.toString(),
                    "injectedAreaFills" to injectedAreaFillCount.toString(),
                    "suppressedRasterAreaPatterns" to suppressedRasterAreaPatternCount.toString(),
                    "suppressedText" to suppressedTextCount.toString(),
                    "suppressedSoundings" to suppressedSoundingCount.toString()
                )
            )
        )
    }
}

internal fun buildBrowserS52DisplayCommandPlan(
    commands: List<S52DrawCommand>,
    presLib: PresLibPack,
    viewport: RenderViewport,
    widthPx: Int,
    heightPx: Int,
    scaleDenominator: Double
): BrowserS52DisplayCommandPlan {
    val baseCommands = ArrayList<S52DrawCommand>(commands.size)
    val labelCommands = ArrayList<S52DrawCommand>()
    var injectedAreaFillCount = 0
    var suppressedRasterAreaPatternCount = 0

    for (command in commands) {
        when (command) {
            is S52DrawCommand.AreaPattern -> {
                val backgroundColor = command.backgroundColorToken
                if (!backgroundColor.isNullOrBlank()) {
                    baseCommands += S52DrawCommand.AreaFill(
                        featureId = command.featureId,
                        geometry = command.geometry,
                        colorToken = backgroundColor,
                        priority = command.priority,
                        viewingGroup = command.viewingGroup,
                        category = command.category,
                        overRadar = command.overRadar
                    )
                    injectedAreaFillCount++
                }
                // Do not let browser raster/vector pattern tiles draw on top of solid
                // fills in the interactive demo.  Several S-52/OpenCPN pattern
                // assets include a framed tile background; repeating that tile
                // makes the chart look like the same area was rendered with
                // rounded rectangles.  Keep the area fill, drop the tile.
                suppressedRasterAreaPatternCount++
            }
            is S52DrawCommand.Text,
            is S52DrawCommand.Sounding -> labelCommands += command
            else -> baseCommands += command
        }
    }

    val keptLabels = deconflictLabels(
        commands = labelCommands,
        viewport = viewport,
        widthPx = widthPx,
        heightPx = heightPx,
        scaleDenominator = scaleDenominator
    )
    val keptTextCount = keptLabels.count { it is S52DrawCommand.Text }
    val keptSoundingCount = keptLabels.count { it is S52DrawCommand.Sounding }
    val originalTextCount = labelCommands.count { it is S52DrawCommand.Text }
    val originalSoundingCount = labelCommands.count { it is S52DrawCommand.Sounding }

    return BrowserS52DisplayCommandPlan(
        commands = baseCommands,
        labelCommands = keptLabels,
        originalCommandCount = commands.size,
        injectedAreaFillCount = injectedAreaFillCount,
        suppressedRasterAreaPatternCount = suppressedRasterAreaPatternCount,
        suppressedTextCount = originalTextCount - keptTextCount,
        suppressedSoundingCount = originalSoundingCount - keptSoundingCount
    )
}

private fun deconflictLabels(
    commands: List<S52DrawCommand>,
    viewport: RenderViewport,
    widthPx: Int,
    heightPx: Int,
    scaleDenominator: Double
): List<S52DrawCommand> {
    if (commands.isEmpty()) return emptyList()
    val occupied = ArrayList<LabelBox>()
    val kept = ArrayList<S52DrawCommand>()
    val maxText = maxTextLabels(scaleDenominator, widthPx, heightPx)
    val maxSounding = maxSoundingLabels(scaleDenominator, widthPx, heightPx)
    var textCount = 0
    var soundingCount = 0
    val sorted = commands.sortedWith(
        compareByDescending<S52DrawCommand> { it.priority }
            .thenByDescending { it.viewingGroup }
            .thenBy { it.labelText().length }
    )
    val seenTextCells = mutableSetOf<String>()
    for (command in sorted) {
        val text = command.labelText()
        if (text.isBlank()) continue
        val isSounding = command is S52DrawCommand.Sounding
        if (isSounding) {
            if (soundingCount >= maxSounding) continue
        } else if (textCount >= maxText) {
            continue
        }
        val anchor = command.geometry.labelAnchor() ?: continue
        val point = project(anchor, viewport, widthPx, heightPx)
        if (point.x < -40.0 || point.y < -24.0 || point.x > widthPx + 40.0 || point.y > heightPx + 24.0) continue
        val normalizedText = text.normalizedLabelText()
        val cellKey = labelCellKey(normalizedText, point, isSounding)
        if (!isSounding && !seenTextCells.add(cellKey)) continue
        val box = labelBox(point, normalizedText, isSounding)
        if (occupied.any { it.intersects(box) }) continue
        occupied += box
        kept += command
        if (isSounding) soundingCount++ else textCount++
    }
    return kept.sortedWith(compareBy<S52DrawCommand> { it.priority }.thenBy { it.viewingGroup })
}

private fun maxTextLabels(scaleDenominator: Double, widthPx: Int, heightPx: Int): Int {
    val areaFactor = ((widthPx * heightPx).toDouble() / (1280.0 * 720.0)).coerceIn(0.5, 4.0)
    val base = when {
        scaleDenominator <= 10_000.0 -> 120
        scaleDenominator <= 25_000.0 -> 86
        scaleDenominator <= 75_000.0 -> 54
        else -> 28
    }
    return (base * areaFactor).toInt().coerceAtLeast(12)
}

private fun maxSoundingLabels(scaleDenominator: Double, widthPx: Int, heightPx: Int): Int {
    val areaFactor = ((widthPx * heightPx).toDouble() / (1280.0 * 720.0)).coerceIn(0.5, 4.0)
    val base = when {
        scaleDenominator <= 10_000.0 -> 140
        scaleDenominator <= 25_000.0 -> 92
        scaleDenominator <= 75_000.0 -> 50
        else -> 20
    }
    return (base * areaFactor).toInt().coerceAtLeast(8)
}

private fun S52DrawCommand.labelText(): String = when (this) {
    is S52DrawCommand.Text -> textExpression.ifBlank { rawArgs.firstOrNull()?.toString().orEmpty() }
    is S52DrawCommand.Sounding -> depthLabel
    else -> ""
}

private fun String.normalizedLabelText(): String = trim().replace(Regex("\\s+"), " ").take(48)

private fun labelCellKey(text: String, point: ScreenPoint, sounding: Boolean): String {
    val grid = if (sounding) 56.0 else 96.0
    val gx = (point.x / grid).toInt()
    val gy = (point.y / grid).toInt()
    return text.uppercase() + "@" + gx + ":" + gy
}

private fun labelBox(point: ScreenPoint, text: String, sounding: Boolean): LabelBox {
    val charWidth = if (sounding) 7.0 else 6.8
    val height = if (sounding) 16.0 else 18.0
    val width = (text.length.coerceIn(1, 36) * charWidth + 12.0).coerceAtMost(if (sounding) 58.0 else 260.0)
    val pad = if (sounding) 6.0 else 12.0
    val x0 = point.x - width * 0.5 - pad
    val x1 = point.x + width * 0.5 + pad
    val y0 = point.y - height * 0.5 - pad
    val y1 = point.y + height * 0.5 + pad
    return LabelBox(x0, y0, x1, y1)
}

private data class LabelBox(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {
    fun intersects(other: LabelBox): Boolean = x0 <= other.x1 && x1 >= other.x0 && y0 <= other.y1 && y1 >= other.y0
}

private fun EncGeometry.labelAnchor(): Coordinate? = when (this) {
    is EncGeometry.Point -> coordinate
    is EncGeometry.MultiPoint -> coordinates.firstOrNull()
    is EncGeometry.LineString -> coordinates.middleCoordinate()
    is EncGeometry.Polygon -> outer.centroidCoordinate()
}

private fun List<Coordinate>.middleCoordinate(): Coordinate? = when {
    isEmpty() -> null
    size == 1 -> first()
    else -> this[size / 2]
}

private fun List<Coordinate>.centroidCoordinate(): Coordinate? {
    if (isEmpty()) return null
    var lon = 0.0
    var lat = 0.0
    for (coordinate in this) {
        lon += coordinate.lon
        lat += coordinate.lat
    }
    return Coordinate(lon / size.toDouble(), lat / size.toDouble())
}

private fun project(coordinate: Coordinate, viewport: RenderViewport, widthPx: Int, heightPx: Int): ScreenPoint {
    val lonSpan = (viewport.east - viewport.west).takeIf { abs(it) > 1e-12 } ?: 1.0
    val latSpan = (viewport.north - viewport.south).takeIf { abs(it) > 1e-12 } ?: 1.0
    val x = ((coordinate.lon - viewport.west) / lonSpan) * widthPx.toDouble()
    val y = ((viewport.north - coordinate.lat) / latSpan) * heightPx.toDouble()
    return ScreenPoint(x, y)
}
