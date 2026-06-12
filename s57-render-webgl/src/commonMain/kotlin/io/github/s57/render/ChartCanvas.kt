package io.github.s57.render

import io.github.s57.core.GeoBounds
import io.github.s57.core.GeoPoint
import io.github.s57.core.S57CellSummary
import kotlin.math.max

/**
 * Public intermediate layer for an interactive chart canvas.
 *
 * The canvas owns view state for a rectangular drawing area, accepts commands
 * from UI/tooling code, publishes semantic events back to clients, and delegates
 * actual frame preparation to [S57WebGlEngine]. Platform code supplies a
 * [ChartCanvasFrameRenderer] to paint prepared frames into the native canvas.
 */
interface ChartCanvas {
    fun dispatch(command: ChartCanvasCommand): ChartCanvasStatus
    fun status(): ChartCanvasStatus
    fun addListener(listener: ChartCanvasEventListener): ChartCanvasSubscription
}

fun interface ChartCanvasEventListener {
    fun onCanvasEvent(event: ChartCanvasEvent)
}

fun interface ChartCanvasSubscription {
    fun unsubscribe()
}

fun interface ChartCanvasFrameRenderer {
    fun draw(frame: StaticChartFrame): RenderedFrameSummary
}

sealed class ChartCanvasCommand {
    data class ShowCharts(val chartIds: List<String>, val fitToFirstChart: Boolean = true, val redraw: Boolean = true) : ChartCanvasCommand()
    data class SetPalette(val paletteName: String, val redraw: Boolean = true) : ChartCanvasCommand()
    data class SetScale(val scaleDenominator: Double, val redraw: Boolean = true) : ChartCanvasCommand()
    data class Resize(val size: ScreenSize) : ChartCanvasCommand()
    data class SetView(val bounds: GeoBounds, val scaleDenominator: Double? = null, val redraw: Boolean = true) : ChartCanvasCommand()
    data class Zoom(val factor: Double, val focus: ScreenPoint? = null, val redraw: Boolean = true) : ChartCanvasCommand()
    data class Scroll(val delta: ScreenDelta, val redraw: Boolean = true) : ChartCanvasCommand()
    data class MoveCursor(val point: ScreenPoint?) : ChartCanvasCommand()
    data class Center(val location: GeoPoint? = null) : ChartCanvasCommand()
    data class Press(val point: ScreenPoint, val radiusPx: Double = 12.0) : ChartCanvasCommand()
    data class Render(val reason: String = "requested") : ChartCanvasCommand()
}

sealed class ChartCanvasEvent {
    data class StatusChanged(val status: ChartCanvasStatus) : ChartCanvasEvent()
    data class ChartRendered(
        val reason: String,
        val result: S57EngineRenderResult,
        val drawing: RenderedFrameSummary,
        val status: ChartCanvasStatus
    ) : ChartCanvasEvent()
    data class CursorMoved(val point: ScreenPoint?, val geoPoint: GeoPoint?) : ChartCanvasEvent()
    data class Pressed(val point: ScreenPoint, val geoPoint: GeoPoint?, val hits: List<ChartHitResult>) : ChartCanvasEvent()
    data class ObjectSelected(val point: ScreenPoint, val hit: ChartHitResult, val hits: List<ChartHitResult>) : ChartCanvasEvent()
    data class CommandRejected(val command: ChartCanvasCommand, val reason: String, val status: ChartCanvasStatus) : ChartCanvasEvent()
}

data class ChartCanvasStatus(
    val rectangle: ScreenSize,
    val displayedChartIds: List<String> = emptyList(),
    val activeChartId: String? = displayedChartIds.firstOrNull(),
    val scaleDenominator: Double? = null,
    val paletteName: String = "daybright",
    val cursor: ScreenPoint? = null,
    val cursorGeoPoint: GeoPoint? = null,
    val center: GeoPoint? = null,
    val bounds: GeoBounds? = null,
    val lastRenderMessage: String? = null,
    val lastSelectedObject: ChartHitResult? = null
)

class S57ChartCanvas(
    private val engine: S57WebGlEngine,
    private val frameRenderer: ChartCanvasFrameRenderer,
    initialSize: ScreenSize,
    initialPaletteName: String = "daybright"
) : ChartCanvas {
    private val listeners = mutableListOf<ChartCanvasEventListener>()
    private var status = ChartCanvasStatus(rectangle = initialSize, paletteName = normalizePaletteName(initialPaletteName))
    private var currentRequest: ChartRenderRequest? = null
    private var latestFrame: StaticChartFrame? = null

    override fun dispatch(command: ChartCanvasCommand): ChartCanvasStatus {
        when (command) {
            is ChartCanvasCommand.ShowCharts -> showCharts(command.chartIds, command.fitToFirstChart, command.redraw, command)
            is ChartCanvasCommand.SetPalette -> {
                val normalizedPalette = normalizePaletteName(command.paletteName)
                updateStatus(redraw = command.redraw && normalizedPalette != status.paletteName, reason = "palette change") {
                    copy(paletteName = normalizedPalette)
                }
            }
            is ChartCanvasCommand.SetScale -> if (command.scaleDenominator > 0.0) {
                val nextScale = boundedScale(command.scaleDenominator)
                val previousScale = status.scaleDenominator ?: currentRequest?.scaleDenominator
                updateViewBoundsForScale(nextScale)
                updateStatus(redraw = command.redraw && previousScale != nextScale, reason = "scale change") { copy(scaleDenominator = nextScale) }
            } else reject(command, "Scale denominator must be positive")
            is ChartCanvasCommand.Resize -> {
                status = status.copy(rectangle = command.size)
                currentRequest = currentRequest?.copy(widthPx = command.size.widthPx, heightPx = command.size.heightPx)
                refitActiveChartIfNeeded()
                emit(ChartCanvasEvent.StatusChanged(status))
            }
            is ChartCanvasCommand.SetView -> {
                setView(command.bounds, command.scaleDenominator)
                if (command.redraw) render("view change")
            }
            is ChartCanvasCommand.Zoom -> if (command.factor > 0.0) {
                zoom(command.factor, command.focus)
                if (command.redraw) render("zoom")
            } else reject(command, "Zoom factor must be positive")
            is ChartCanvasCommand.Scroll -> {
                scroll(command.delta)
                if (command.redraw) render("scroll")
            }
            is ChartCanvasCommand.MoveCursor -> moveCursor(command.point)
            is ChartCanvasCommand.Center -> {
                center(command.location)
                render("center")
            }
            is ChartCanvasCommand.Press -> press(command.point, command.radiusPx)
            is ChartCanvasCommand.Render -> render(command.reason)
        }
        return status
    }

    override fun status(): ChartCanvasStatus = status

    override fun addListener(listener: ChartCanvasEventListener): ChartCanvasSubscription {
        listeners += listener
        return ChartCanvasSubscription { listeners.remove(listener) }
    }

    private fun showCharts(chartIds: List<String>, fitToFirstChart: Boolean, redraw: Boolean, command: ChartCanvasCommand) {
        val normalizedIds = chartIds.mapNotNull { it.takeIf(String::isNotBlank) }.distinct()
        status = status.copy(displayedChartIds = normalizedIds, activeChartId = normalizedIds.firstOrNull())
        if (normalizedIds.isEmpty()) {
            currentRequest = null
            latestFrame = null
            emit(ChartCanvasEvent.StatusChanged(status))
            return
        }
        if (fitToFirstChart) {
            val cells = displayedCells()
            val fitBounds = cells.mapNotNull { it.bounds }.unionOrNull()
            val active = cells.firstOrNull()
            if (active == null || fitBounds == null) {
                reject(command, "Displayed charts are not imported or have no bounds")
                return
            }
            fitCharts(active, fitBounds)
        }
        if (redraw) render("show charts")
    }

    private fun updateStatus(redraw: Boolean, reason: String, block: ChartCanvasStatus.() -> ChartCanvasStatus) {
        status = status.block()
        currentRequest = currentRequest?.copy(paletteName = status.paletteName, scaleDenominator = status.scaleDenominator ?: itScale())
        emit(ChartCanvasEvent.StatusChanged(status))
        if (redraw) render(reason)
    }

    private fun itScale(): Double = status.scaleDenominator ?: currentRequest?.scaleDenominator ?: 40_000.0

    private fun fitCell(cell: S57CellSummary) {
        fitCharts(cell, requireNotNull(cell.bounds) { "Cell ${cell.cellId} has no bounds" })
    }

    private fun fitCharts(activeCell: S57CellSummary, bounds: GeoBounds) {
        val fit = chartViewportFitForBounds(bounds, status.rectangle, paddingFraction = 0.08)
        val request = ChartRenderRequest(
            cellId = activeCell.cellId,
            bounds = fit.fittedBounds,
            widthPx = status.rectangle.widthPx,
            heightPx = status.rectangle.heightPx,
            scaleDenominator = fit.scaleDenominator,
            paletteName = status.paletteName,
            camera = ChartCameraState(
                center = fit.cameraCenter,
                zoom = fit.scaleDenominator,
                viewport = status.rectangle
            ),
            centerCrosshair = CenterCrosshairConfig(enabled = true, queryOnRender = false),
            depthMesh = DepthMeshConfig(enabled = false),
            renderMode = ChartRenderMode.Flat2D,
            cellIds = status.displayedChartIds.ifEmpty { listOf(activeCell.cellId) }
        )
        currentRequest = request
        status = status.copy(
            activeChartId = activeCell.cellId,
            scaleDenominator = request.scaleDenominator,
            center = request.camera.center,
            bounds = request.bounds,
            cursorGeoPoint = status.cursor?.let { chartProjectionFrom(request).unproject(it) }
        )
        emit(ChartCanvasEvent.StatusChanged(status))
    }

    private fun render(reason: String) {
        val request = currentRequest ?: activeCell()?.takeIf { it.bounds != null }?.also(::fitCell)?.let { currentRequest }
        if (request == null) {
            reject(ChartCanvasCommand.Render(reason), "No active chart request is available")
            return
        }
        val refreshedRequest = request.copy(
            paletteName = status.paletteName,
            cellIds = status.displayedChartIds.ifEmpty { request.renderCellIds },
            scaleDenominator = status.scaleDenominator ?: request.scaleDenominator,
            camera = request.camera.copy(
                center = status.center ?: request.camera.center,
                zoom = status.scaleDenominator ?: request.camera.zoom,
                viewport = status.rectangle
            ),
            widthPx = status.rectangle.widthPx,
            heightPx = status.rectangle.heightPx
        )
        currentRequest = refreshedRequest
        val result = engine.render(refreshedRequest)
        latestFrame = result.frame
        val drawing = frameRenderer.draw(result.frame)
        status = status.copy(
            scaleDenominator = refreshedRequest.scaleDenominator,
            center = refreshedRequest.camera.center,
            bounds = refreshedRequest.bounds,
            lastRenderMessage = drawing.message,
            cursorGeoPoint = status.cursor?.let { chartProjectionFrom(refreshedRequest).unproject(it) }
        )
        emit(ChartCanvasEvent.ChartRendered(reason, result, drawing, status))
        emit(ChartCanvasEvent.StatusChanged(status))
    }

    private fun setView(bounds: GeoBounds, scaleOverride: Double?) {
        val request = currentRequest ?: return
        val scale = scaleOverride?.let(::boundedScale) ?: estimateScaleDenominator(bounds, status.rectangle)
        val center = bounds.center()
        currentRequest = request.copy(
            bounds = bounds,
            scaleDenominator = scale,
            camera = request.camera.copy(center = center, zoom = scale, viewport = status.rectangle)
        )
        status = status.copy(bounds = bounds, center = center, scaleDenominator = scale)
        emit(ChartCanvasEvent.StatusChanged(status))
    }

    private fun zoom(factor: Double, focus: ScreenPoint?) {
        val request = currentRequest ?: return
        val oldBounds = request.bounds
        val focusGeo = focus?.let { chartProjectionFrom(request).unproject(it) } ?: oldBounds.center()
        val scale = boundedScale((status.scaleDenominator ?: request.scaleDenominator) / factor)
        val spanFactor = (scale / request.scaleDenominator).coerceIn(0.01, 100.0)
        val newBounds = oldBounds.scaledAround(focusGeo, spanFactor)
        val newCenter = newBounds.center()
        currentRequest = request.copy(
            bounds = newBounds,
            scaleDenominator = scale,
            camera = request.camera.copy(center = newCenter, zoom = scale)
        )
        status = status.copy(scaleDenominator = scale, center = newCenter, bounds = newBounds)
        emit(ChartCanvasEvent.StatusChanged(status))
    }

    private fun updateViewBoundsForScale(scale: Double) {
        val request = currentRequest ?: return
        val oldScale = status.scaleDenominator ?: request.scaleDenominator
        val center = status.center ?: request.bounds.center()
        val spanFactor = (scale / oldScale).coerceIn(0.01, 100.0)
        val newBounds = request.bounds.scaledAround(center, spanFactor)
        currentRequest = request.copy(bounds = newBounds, scaleDenominator = scale, camera = request.camera.copy(center = newBounds.center(), zoom = scale))
        status = status.copy(bounds = newBounds, center = newBounds.center(), scaleDenominator = scale)
    }

    private fun scroll(delta: ScreenDelta) {
        val request = currentRequest ?: return
        val lonSpan = request.bounds.maxLon - request.bounds.minLon
        val latSpan = request.bounds.maxLat - request.bounds.minLat
        val dxLon = delta.dx / max(1.0, request.widthPx.toDouble()) * lonSpan
        val dyLat = -delta.dy / max(1.0, request.heightPx.toDouble()) * latSpan
        val newBounds = request.bounds.translated(dxLon, dyLat)
        currentRequest = request.copy(bounds = newBounds, camera = request.camera.copy(center = newBounds.center()))
        status = status.copy(bounds = newBounds, center = newBounds.center())
        emit(ChartCanvasEvent.StatusChanged(status))
    }

    private fun moveCursor(point: ScreenPoint?) {
        val geo = point?.let { screenPoint -> currentRequest?.let { request -> chartProjectionFrom(request).unproject(screenPoint) } }
        status = status.copy(cursor = point, cursorGeoPoint = geo)
        emit(ChartCanvasEvent.CursorMoved(point, geo))
        emit(ChartCanvasEvent.StatusChanged(status))
    }

    private fun center(location: GeoPoint?) {
        val request = currentRequest ?: return
        val target = location ?: activeCell()?.bounds?.center() ?: request.bounds.center()
        val lonSpan = request.bounds.maxLon - request.bounds.minLon
        val latSpan = request.bounds.maxLat - request.bounds.minLat
        val newBounds = GeoBounds(target.lon - lonSpan / 2.0, target.lat - latSpan / 2.0, target.lon + lonSpan / 2.0, target.lat + latSpan / 2.0)
        currentRequest = request.copy(bounds = newBounds, camera = request.camera.copy(center = target))
        status = status.copy(center = target, bounds = newBounds)
        emit(ChartCanvasEvent.StatusChanged(status))
    }

    private fun press(point: ScreenPoint, radiusPx: Double) {
        moveCursor(point)
        val frame = latestFrame ?: run {
            render("press hit-test")
            latestFrame
        }
        val hits = frame?.hitTester()?.hitTest(point, radiusPx) ?: emptyList()
        val geo = currentRequest?.let { chartProjectionFrom(it).unproject(point) }
        status = status.copy(lastSelectedObject = hits.firstOrNull())
        emit(ChartCanvasEvent.Pressed(point, geo, hits))
        hits.firstOrNull()?.let { emit(ChartCanvasEvent.ObjectSelected(point, it, hits)) }
        emit(ChartCanvasEvent.StatusChanged(status))
    }

    private fun refitActiveChartIfNeeded() {
        val cell = activeCell()
        if (cell != null && currentRequest == null) fitCell(cell)
    }

    private fun displayedCells(): List<S57CellSummary> {
        val byId = engine.listCells().associateBy { it.cellId }
        return status.displayedChartIds.mapNotNull { byId[it] }
    }

    private fun activeCell(): S57CellSummary? = status.activeChartId?.let { id -> engine.listCells().firstOrNull { it.cellId == id } }

    private fun reject(command: ChartCanvasCommand, reason: String) {
        emit(ChartCanvasEvent.CommandRejected(command, reason, status))
    }

    private fun emit(event: ChartCanvasEvent) {
        listeners.toList().forEach { it.onCanvasEvent(event) }
    }
}

private fun GeoBounds.scaledAround(anchor: GeoPoint, spanFactor: Double): GeoBounds = GeoBounds(
    minLon = anchor.lon + (minLon - anchor.lon) * spanFactor,
    minLat = anchor.lat + (minLat - anchor.lat) * spanFactor,
    maxLon = anchor.lon + (maxLon - anchor.lon) * spanFactor,
    maxLat = anchor.lat + (maxLat - anchor.lat) * spanFactor
)

private fun GeoBounds.translated(deltaLon: Double, deltaLat: Double): GeoBounds = GeoBounds(
    minLon = minLon + deltaLon,
    minLat = minLat + deltaLat,
    maxLon = maxLon + deltaLon,
    maxLat = maxLat + deltaLat
)


private fun List<GeoBounds>.unionOrNull(): GeoBounds? {
    if (isEmpty()) return null
    var minLon = first().minLon
    var minLat = first().minLat
    var maxLon = first().maxLon
    var maxLat = first().maxLat
    for (bounds in drop(1)) {
        minLon = kotlin.math.min(minLon, bounds.minLon)
        minLat = kotlin.math.min(minLat, bounds.minLat)
        maxLon = kotlin.math.max(maxLon, bounds.maxLon)
        maxLat = kotlin.math.max(maxLat, bounds.maxLat)
    }
    return GeoBounds(minLon, minLat, maxLon, maxLat)
}
