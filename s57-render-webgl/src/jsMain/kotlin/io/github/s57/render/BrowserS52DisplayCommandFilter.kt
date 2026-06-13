package io.github.s57.render

import io.github.s52.core.draw.S52DrawCommand
import io.github.s52.core.geometry.Coordinate
import io.github.s52.core.geometry.EncGeometry
import io.github.s52.preslib.PresLibPack
import io.github.s52.render.webgl.RenderViewport
import io.github.s57.core.S57Feature
import io.github.s57.core.S57Value
import kotlin.math.abs
import kotlin.math.hypot

internal data class BrowserS52DisplayCommandPlan(
    val commands: List<S52DrawCommand>,
    val textCommands: List<S52DrawCommand>,
    val originalCommandCount: Int,
    val suppressedRasterAreaPatternCount: Int,
    val suppressedDuplicatePointSymbolCount: Int,
    val reorderedAreaFillCount: Int,
    val declutterProfile: String = "detail",
    val suppressedScalePointSymbolCount: Int = 0,
    val suppressedTextDeclutterCount: Int = 0,
    val suppressedSoundingDeclutterCount: Int = 0,
    val suppressedVectorAreaPatternDeclutterCount: Int = 0
) {
    val changed: Boolean get() = suppressedRasterAreaPatternCount > 0 ||
        suppressedDuplicatePointSymbolCount > 0 ||
        suppressedScalePointSymbolCount > 0 ||
        suppressedTextDeclutterCount > 0 ||
        suppressedSoundingDeclutterCount > 0 ||
        suppressedVectorAreaPatternDeclutterCount > 0 ||
        reorderedAreaFillCount > 0 ||
        textCommands.isNotEmpty()

    fun diagnostics(cellId: String): List<RenderPipelineDiagnostic> {
        if (!changed) return emptyList()
        return listOf(
            RenderPipelineDiagnostic(
                stage = RenderPipelineStage.S52Portrayal,
                severity = RenderPipelineSeverity.Info,
                code = "s52.strict_single_webgl_display_plan",
                message = "Strict S-52 WebGL-only display plan: profile=$declutterProfile original=$originalCommandCount webGlCommands=${commands.size} webGlTextCommands=${textCommands.size} suppressedRasterAreaPatterns=$suppressedRasterAreaPatternCount suppressedDuplicatePointSymbols=$suppressedDuplicatePointSymbolCount suppressedScalePointSymbols=$suppressedScalePointSymbolCount suppressedText=$suppressedTextDeclutterCount suppressedSoundings=$suppressedSoundingDeclutterCount suppressedVectorAreaPatterns=$suppressedVectorAreaPatternDeclutterCount reorderedAreaFills=$reorderedAreaFillCount",
                source = RenderPipelineSource(cellId = cellId),
                metadata = mapOf(
                    "declutterProfile" to declutterProfile,
                    "originalCommands" to originalCommandCount.toString(),
                    "renderedCommands" to commands.size.toString(),
                    "webGlTextCommands" to textCommands.size.toString(),
                    "suppressedRasterAreaPatterns" to suppressedRasterAreaPatternCount.toString(),
                    "suppressedDuplicatePointSymbols" to suppressedDuplicatePointSymbolCount.toString(),
                    "suppressedScalePointSymbols" to suppressedScalePointSymbolCount.toString(),
                    "suppressedText" to suppressedTextDeclutterCount.toString(),
                    "suppressedSoundings" to suppressedSoundingDeclutterCount.toString(),
                    "suppressedVectorAreaPatterns" to suppressedVectorAreaPatternDeclutterCount.toString(),
                    "reorderedAreaFills" to reorderedAreaFillCount.toString()
                )
            )
        )
    }
}

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
    val declutter = BrowserS52DeclutterProfile.forScale(scaleDenominator)
    val bestPointSymbols = selectBestPointSymbolByFeature(commands)
    val displayPointSymbols = selectDisplayPointSymbolsByFeature(bestPointSymbols.values, objectClassByFeatureId, viewport, widthPx, heightPx, declutter)
    val filtered = ArrayList<S52DrawCommand>(commands.size)
    val textCommands = ArrayList<S52DrawCommand>()
    val textGrid = BrowserS52ScreenTileSet(declutter.textTilePx)
    val soundingGrid = BrowserS52ScreenTileSet(declutter.soundingTilePx)
    val areaPatternGrid = BrowserS52ScreenTileSet(declutter.areaPatternTilePx)
    var emittedVectorAreaPatterns = 0
    var suppressedDuplicatePointSymbols = 0
    var suppressedScalePointSymbols = 0
    var suppressedRasterAreaPatterns = 0
    var suppressedTextDeclutter = 0
    var suppressedSoundingDeclutter = 0
    var suppressedVectorAreaPatternDeclutter = 0

    for (command in commands) {
        when (command) {
            is S52DrawCommand.PointSymbol -> {
                val best = bestPointSymbols[command.featureId]
                val display = displayPointSymbols[command.featureId]
                when {
                    best == null || best != command -> suppressedDuplicatePointSymbols++
                    display == command -> filtered += command
                    else -> suppressedScalePointSymbols++
                }
            }
            is S52DrawCommand.Text -> {
                val resolved = command.resolveDisplayLabel(featureById)
                if (resolved == null) {
                    // unresolved text was deliberately suppressed before display planning
                } else if (resolved.isDeclutteredText(featureById, objectClassByFeatureId, viewport, widthPx, heightPx, declutter, textGrid)) {
                    suppressedTextDeclutter++
                } else {
                    textCommands += resolved
                }
            }
            is S52DrawCommand.Sounding -> {
                if (command.isDeclutteredSounding(viewport, widthPx, heightPx, declutter, soundingGrid)) {
                    suppressedSoundingDeclutter++
                } else {
                    textCommands += command
                }
            }
            is S52DrawCommand.AreaPattern -> {
                if (command.isBrowserRasterPattern(presLib)) {
                    suppressedRasterAreaPatterns++
                } else if (command.isDeclutteredVectorAreaPattern(objectClassByFeatureId, viewport, widthPx, heightPx, declutter, areaPatternGrid, emittedVectorAreaPatterns)) {
                    suppressedVectorAreaPatternDeclutter++
                } else {
                    emittedVectorAreaPatterns++
                    filtered += command
                }
            }
            else -> filtered += command
        }
    }

    val ordered = filtered.sortedWith(StrictBrowserS52PainterOrder(objectClassByFeatureId))
    val reorderedAreaFills = countReorderedAreaFills(filtered, ordered)
    return BrowserS52DisplayCommandPlan(
        commands = ordered,
        textCommands = textCommands,
        originalCommandCount = commands.size,
        suppressedRasterAreaPatternCount = suppressedRasterAreaPatterns,
        suppressedDuplicatePointSymbolCount = suppressedDuplicatePointSymbols,
        reorderedAreaFillCount = reorderedAreaFills,
        declutterProfile = declutter.name,
        suppressedScalePointSymbolCount = suppressedScalePointSymbols,
        suppressedTextDeclutterCount = suppressedTextDeclutter,
        suppressedSoundingDeclutterCount = suppressedSoundingDeclutter,
        suppressedVectorAreaPatternDeclutterCount = suppressedVectorAreaPatternDeclutter
    )
}

private data class BrowserS52DeclutterProfile(
    val name: String,
    val symbolTilePx: Int,
    val criticalSymbolTilePx: Int,
    val textTilePx: Int,
    val hideNonCriticalText: Boolean,
    val soundingTilePx: Int,
    val areaPatternTilePx: Int,
    val maxVectorAreaPatterns: Int
) {
    companion object {
        fun forScale(scaleDenominator: Double): BrowserS52DeclutterProfile = when {
            scaleDenominator >= 100_000.0 -> BrowserS52DeclutterProfile(
                name = "overview",
                symbolTilePx = 88,
                criticalSymbolTilePx = 42,
                textTilePx = 180,
                hideNonCriticalText = true,
                soundingTilePx = 150,
                areaPatternTilePx = 320,
                maxVectorAreaPatterns = 24
            )
            scaleDenominator >= 50_000.0 -> BrowserS52DeclutterProfile(
                name = "harbor-overview",
                symbolTilePx = 62,
                criticalSymbolTilePx = 30,
                textTilePx = 140,
                hideNonCriticalText = true,
                soundingTilePx = 96,
                areaPatternTilePx = 240,
                maxVectorAreaPatterns = 48
            )
            scaleDenominator >= 25_000.0 -> BrowserS52DeclutterProfile(
                name = "harbor",
                symbolTilePx = 44,
                criticalSymbolTilePx = 24,
                textTilePx = 104,
                hideNonCriticalText = false,
                soundingTilePx = 72,
                areaPatternTilePx = 180,
                maxVectorAreaPatterns = 96
            )
            scaleDenominator >= 10_000.0 -> BrowserS52DeclutterProfile(
                name = "approach",
                symbolTilePx = 28,
                criticalSymbolTilePx = 0,
                textTilePx = 80,
                hideNonCriticalText = false,
                soundingTilePx = 52,
                areaPatternTilePx = 0,
                maxVectorAreaPatterns = Int.MAX_VALUE
            )
            else -> BrowserS52DeclutterProfile(
                name = "detail",
                symbolTilePx = 0,
                criticalSymbolTilePx = 0,
                textTilePx = 0,
                hideNonCriticalText = false,
                soundingTilePx = 0,
                areaPatternTilePx = 0,
                maxVectorAreaPatterns = Int.MAX_VALUE
            )
        }
    }
}

private data class BrowserS52PlanPixelPoint(val x: Double, val y: Double)
private data class BrowserS52PlanPixelBounds(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    val area: Double get() = (maxX - minX).coerceAtLeast(0.0) * (maxY - minY).coerceAtLeast(0.0)
    fun outside(width: Double, height: Double): Boolean = maxX < 0.0 || minX > width || maxY < 0.0 || minY > height
}

private class BrowserS52ScreenTileSet(private val tilePx: Int) {
    private val occupied = hashSetOf<Long>()

    fun accept(point: BrowserS52PlanPixelPoint): Boolean {
        if (tilePx <= 0) return true
        val key = key(point)
        return occupied.add(key)
    }

    private fun key(point: BrowserS52PlanPixelPoint): Long {
        val x = (point.x / tilePx.toDouble()).toInt()
        val y = (point.y / tilePx.toDouble()).toInt()
        return (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)
    }
}

private class BrowserS52TileWinners<T : S52DrawCommand>(private val tilePx: Int) {
    private val winners = linkedMapOf<Long, BrowserS52TileWinner<T>>()

    fun put(point: BrowserS52PlanPixelPoint, command: T, score: Int) {
        if (tilePx <= 0) {
            winners[command.featureId] = BrowserS52TileWinner(command, score)
            return
        }
        val key = key(point)
        val current = winners[key]
        if (current == null || score > current.score) winners[key] = BrowserS52TileWinner(command, score)
    }

    fun commands(): List<T> = winners.values.map { it.command }

    private fun key(point: BrowserS52PlanPixelPoint): Long {
        val x = (point.x / tilePx.toDouble()).toInt()
        val y = (point.y / tilePx.toDouble()).toInt()
        return (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)
    }
}

private data class BrowserS52TileWinner<T : S52DrawCommand>(val command: T, val score: Int)

private fun selectDisplayPointSymbolsByFeature(
    symbols: Collection<S52DrawCommand.PointSymbol>,
    objectClassByFeatureId: Map<Long, String>,
    viewport: RenderViewport,
    widthPx: Int,
    heightPx: Int,
    declutter: BrowserS52DeclutterProfile
): Map<Long, S52DrawCommand.PointSymbol> {
    if (declutter.symbolTilePx <= 0) return symbols.associateBy { it.featureId }
    val normalWinners = BrowserS52TileWinners<S52DrawCommand.PointSymbol>(declutter.symbolTilePx)
    val criticalWinners = BrowserS52TileWinners<S52DrawCommand.PointSymbol>(declutter.criticalSymbolTilePx.takeIf { it > 0 } ?: declutter.symbolTilePx)
    for (symbol in symbols) {
        val anchor = symbol.geometry.screenAnchor(viewport, widthPx, heightPx) ?: continue
        if (anchor.outside(widthPx, heightPx)) continue
        val objectClass = objectClassByFeatureId.objectClassFor(symbol.featureId)
        val score = symbol.strictSymbolScore(objectClass)
        if (objectClass.isCriticalSymbolObjectClass()) criticalWinners.put(anchor, symbol, score) else normalWinners.put(anchor, symbol, score)
    }
    return (normalWinners.commands() + criticalWinners.commands()).associateBy { it.featureId }
}

private fun S52DrawCommand.Text.isDeclutteredText(
    featureById: Map<Long, S57Feature>,
    objectClassByFeatureId: Map<Long, String>,
    viewport: RenderViewport,
    widthPx: Int,
    heightPx: Int,
    declutter: BrowserS52DeclutterProfile,
    grid: BrowserS52ScreenTileSet
): Boolean {
    val objectClass = objectClassByFeatureId.objectClassFor(featureId)
    if (declutter.hideNonCriticalText && !objectClass.isCriticalTextObjectClass()) return true
    val anchor = geometry.screenAnchor(viewport, widthPx, heightPx) ?: return true
    if (anchor.outside(widthPx, heightPx)) return true
    val name = featureById[featureId]?.attributeLabel("OBJNAM")
    val longNonCriticalName = declutter.name != "detail" && !objectClass.isCriticalTextObjectClass() && (name?.length ?: textExpression.length) > 18
    if (longNonCriticalName) return true
    return !grid.accept(anchor)
}

private fun S52DrawCommand.Sounding.isDeclutteredSounding(
    viewport: RenderViewport,
    widthPx: Int,
    heightPx: Int,
    declutter: BrowserS52DeclutterProfile,
    grid: BrowserS52ScreenTileSet
): Boolean {
    val anchor = geometry.screenAnchor(viewport, widthPx, heightPx) ?: return true
    if (anchor.outside(widthPx, heightPx)) return true
    return !grid.accept(anchor)
}

private fun S52DrawCommand.AreaPattern.isDeclutteredVectorAreaPattern(
    objectClassByFeatureId: Map<Long, String>,
    viewport: RenderViewport,
    widthPx: Int,
    heightPx: Int,
    declutter: BrowserS52DeclutterProfile,
    grid: BrowserS52ScreenTileSet,
    emittedVectorAreaPatterns: Int
): Boolean {
    if (declutter.areaPatternTilePx <= 0 && emittedVectorAreaPatterns < declutter.maxVectorAreaPatterns) return false
    val objectClass = objectClassByFeatureId.objectClassFor(featureId)
    if (emittedVectorAreaPatterns >= declutter.maxVectorAreaPatterns && !objectClass.isCriticalPatternObjectClass()) return true
    val bounds = geometry.screenBounds(viewport, widthPx, heightPx) ?: return true
    if (bounds.outside(widthPx.toDouble(), heightPx.toDouble())) return true
    if (bounds.area < 256.0 && !objectClass.isCriticalPatternObjectClass()) return true
    val anchor = geometry.screenAnchor(viewport, widthPx, heightPx) ?: return true
    if (objectClass.isCriticalPatternObjectClass() && declutter.name != "overview") return false
    return !grid.accept(anchor)
}

private fun EncGeometry.screenAnchor(viewport: RenderViewport, widthPx: Int, heightPx: Int): BrowserS52PlanPixelPoint? = when (this) {
    is EncGeometry.Point -> coordinate.project(viewport, widthPx, heightPx)
    is EncGeometry.MultiPoint -> coordinates.firstOrNull()?.project(viewport, widthPx, heightPx)
    is EncGeometry.LineString -> coordinates.lineAnchor(viewport, widthPx, heightPx)
    is EncGeometry.Polygon -> outer.polygonAnchor(viewport, widthPx, heightPx)
}

private fun EncGeometry.screenBounds(viewport: RenderViewport, widthPx: Int, heightPx: Int): BrowserS52PlanPixelBounds? {
    val points = when (this) {
        is EncGeometry.Point -> listOf(coordinate.project(viewport, widthPx, heightPx))
        is EncGeometry.MultiPoint -> coordinates.map { it.project(viewport, widthPx, heightPx) }
        is EncGeometry.LineString -> coordinates.map { it.project(viewport, widthPx, heightPx) }
        is EncGeometry.Polygon -> outer.map { it.project(viewport, widthPx, heightPx) }
    }
    if (points.isEmpty()) return null
    return BrowserS52PlanPixelBounds(
        minX = points.minOf { it.x },
        minY = points.minOf { it.y },
        maxX = points.maxOf { it.x },
        maxY = points.maxOf { it.y }
    )
}

private fun Coordinate.project(viewport: RenderViewport, widthPx: Int, heightPx: Int): BrowserS52PlanPixelPoint {
    val x = ((lon - viewport.west) / (viewport.east - viewport.west).coerceAtLeast(1e-12)) * widthPx.toDouble()
    val y = (1.0 - ((lat - viewport.south) / (viewport.north - viewport.south).coerceAtLeast(1e-12))) * heightPx.toDouble()
    return BrowserS52PlanPixelPoint(x, y)
}

private fun List<Coordinate>.lineAnchor(viewport: RenderViewport, widthPx: Int, heightPx: Int): BrowserS52PlanPixelPoint? {
    if (isEmpty()) return null
    val points = map { it.project(viewport, widthPx, heightPx) }
    var total = 0.0
    for (i in 0 until points.lastIndex) total += points[i].distance(points[i + 1])
    if (total <= 1e-9) return points[points.size / 2]
    val half = total * 0.5
    var acc = 0.0
    for (i in 0 until points.lastIndex) {
        val a = points[i]
        val b = points[i + 1]
        val segment = a.distance(b)
        if (acc + segment >= half && segment > 1e-9) {
            val t = ((half - acc) / segment).coerceIn(0.0, 1.0)
            return BrowserS52PlanPixelPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }
        acc += segment
    }
    return points.last()
}

private fun List<Coordinate>.polygonAnchor(viewport: RenderViewport, widthPx: Int, heightPx: Int): BrowserS52PlanPixelPoint? {
    if (isEmpty()) return null
    val points = map { it.project(viewport, widthPx, heightPx) }
    var twiceArea = 0.0
    var cx = 0.0
    var cy = 0.0
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        val cross = a.x * b.y - b.x * a.y
        twiceArea += cross
        cx += (a.x + b.x) * cross
        cy += (a.y + b.y) * cross
    }
    if (abs(twiceArea) > 1e-9) {
        val factor = 1.0 / (3.0 * twiceArea)
        return BrowserS52PlanPixelPoint(cx * factor, cy * factor)
    }
    return BrowserS52PlanPixelPoint(
        x = (points.minOf { it.x } + points.maxOf { it.x }) * 0.5,
        y = (points.minOf { it.y } + points.maxOf { it.y }) * 0.5
    )
}

private fun BrowserS52PlanPixelPoint.distance(other: BrowserS52PlanPixelPoint): Double = hypot(other.x - x, other.y - y)
private fun BrowserS52PlanPixelPoint.outside(widthPx: Int, heightPx: Int): Boolean = x < -32.0 || y < -32.0 || x > widthPx + 32.0 || y > heightPx + 32.0

private fun Map<Long, String>.objectClassFor(featureId: Long): String = this[featureId] ?: this[featureId / 1000L] ?: ""

private fun String.isCriticalSymbolObjectClass(): Boolean = this in CriticalSymbolObjectClasses
private fun String.isCriticalTextObjectClass(): Boolean = this in CriticalTextObjectClasses
private fun String.isCriticalPatternObjectClass(): Boolean = this in CriticalPatternObjectClasses

private val CriticalSymbolObjectClasses = setOf(
    "LIGHTS", "BOYLAT", "BOYSPP", "BOYSAW", "BOYCAR", "BOYISD", "BOYINB",
    "BCNLAT", "BCNSPP", "BCNSAW", "BCNCAR", "BCNISD", "WRECKS", "OBSTRN", "UWTROC",
    "MORFAC", "PILPNT", "ACHBRT"
)

private val CriticalTextObjectClasses = setOf(
    "LIGHTS", "BOYLAT", "BOYSPP", "BOYSAW", "BOYCAR", "BOYISD", "BOYINB",
    "BCNLAT", "BCNSPP", "BCNSAW", "BCNCAR", "BCNISD", "WRECKS", "OBSTRN", "UWTROC",
    "DEPARE", "DRGARE"
)

private val CriticalPatternObjectClasses = setOf(
    "WRECKS", "OBSTRN", "UWTROC", "DRGARE", "RESARE", "CBLARE", "PIPARE", "ACHARE", "MIPARE"
)


private fun S52DrawCommand.AreaPattern.isBrowserRasterPattern(presLib: PresLibPack): Boolean =
    presLib.patterns.find(patternName)?.bitmap != null


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

private fun S52DrawCommand.PointSymbol.strictSymbolScore(objectClass: String = ""): Int {
    val name = symbolName.uppercase()
    var score = priority * 1000 + viewingGroup
    if (objectClass.isCriticalSymbolObjectClass()) score += 250_000
    if (name.contains("LIGHT") || name.contains("TOPMAR") || name.contains("WRECK") || name.contains("DANGER")) score += 50_000
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
