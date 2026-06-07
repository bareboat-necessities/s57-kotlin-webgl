package io.github.s57.render

import io.github.s57.core.GeoPoint
import io.github.s57.core.S57Feature
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Pixel coordinate in the rendered canvas. */
data class ScreenPoint(val x: Double, val y: Double)

data class ScreenDelta(val dx: Double, val dy: Double)

data class ScreenSize(val widthPx: Int, val heightPx: Int)

enum class PointerPhase { Down, Move, Up, Cancel }

enum class PointerKind { Mouse, Touch, Pen, Unknown }

enum class MouseButton { Primary, Secondary, Middle, Other, None }

data class ChartPointerEvent(
    val pointerId: Int,
    val phase: PointerPhase,
    val position: ScreenPoint,
    val kind: PointerKind = PointerKind.Unknown,
    val button: MouseButton = MouseButton.None,
    val timestampMillis: Double = 0.0
)

data class ChartWheelEvent(
    val position: ScreenPoint,
    val deltaX: Double,
    val deltaY: Double,
    val timestampMillis: Double = 0.0
)

enum class ScrollDirection { Up, Down, Left, Right, None }

data class CenterCrosshairConfig(
    val enabled: Boolean = false,
    val queryOnRender: Boolean = false,
    val sizePx: Double = 28.0
)

data class DepthMeshConfig(
    val enabled: Boolean = false,
    val verticalExaggeration: Double = 1.0,
    val minDepthMeters: Double = 0.0,
    val maxDepthMeters: Double = 80.0,
    val gridCellPx: Int = 32
) {
    init {
        require(verticalExaggeration >= 0.0) { "verticalExaggeration must be >= 0" }
        require(gridCellPx > 0) { "gridCellPx must be > 0" }
    }
}

data class ChartCameraState(
    val center: GeoPoint,
    val zoom: Double,
    val rotationDegrees: Double = 0.0,
    val tiltDegrees: Double = 0.0,
    val viewport: ScreenSize = ScreenSize(1280, 720)
) {
    init {
        require(zoom > 0.0) { "zoom must be > 0" }
        require(tiltDegrees in 0.0..65.0) { "tiltDegrees must be between 0 and 65" }
    }

    fun zoomedBy(factor: Double): ChartCameraState = copy(zoom = (zoom * factor).coerceIn(0.01, 1_000_000.0))
    fun rotatedBy(deltaDegrees: Double): ChartCameraState = copy(rotationDegrees = normalizeDegrees(rotationDegrees + deltaDegrees))
    fun tiltedBy(deltaDegrees: Double): ChartCameraState = copy(tiltDegrees = (tiltDegrees + deltaDegrees).coerceIn(0.0, 65.0))

    companion object {
        fun normalizeDegrees(value: Double): Double {
            var v = value % 360.0
            if (v < 0.0) v += 360.0
            return v
        }
    }
}

sealed class ChartUserEvent {
    data class ClickObject(val point: ScreenPoint, val hits: List<ChartHitResult>) : ChartUserEvent()
    data class TouchObject(val point: ScreenPoint, val hits: List<ChartHitResult>) : ChartUserEvent()
    data class CenterCrosshairChanged(val enabled: Boolean) : ChartUserEvent()
    data class CenterCrosshairQuery(val point: ScreenPoint, val hits: List<ChartHitResult>) : ChartUserEvent()
    data class Drag(val from: ScreenPoint, val to: ScreenPoint, val delta: ScreenDelta) : ChartUserEvent()
    data class Hold(val point: ScreenPoint, val durationMillis: Double) : ChartUserEvent()
    data class Scroll(val direction: ScrollDirection, val delta: ScreenDelta, val point: ScreenPoint) : ChartUserEvent()
    data class Zoom(val factor: Double, val focus: ScreenPoint) : ChartUserEvent()
    data class Rotate(val deltaDegrees: Double, val focus: ScreenPoint) : ChartUserEvent()
    data class Tilt(val deltaDegrees: Double, val focus: ScreenPoint) : ChartUserEvent()
}

data class ChartHitResult(
    val featureId: Long,
    val objectClass: String,
    val displayName: String = objectClass,
    val distancePx: Double = 0.0,
    val feature: S57Feature? = null
)

interface ChartInteractionListener {
    fun onUserEvent(event: ChartUserEvent)
}

interface ChartHitTester {
    fun hitTest(point: ScreenPoint, radiusPx: Double = 12.0): List<ChartHitResult>
    fun centerCrosshairHitTest(camera: ChartCameraState, radiusPx: Double = 12.0): List<ChartHitResult> =
        hitTest(ScreenPoint(camera.viewport.widthPx / 2.0, camera.viewport.heightPx / 2.0), radiusPx)
}

class EmptyChartHitTester : ChartHitTester {
    override fun hitTest(point: ScreenPoint, radiusPx: Double): List<ChartHitResult> = emptyList()
}

/**
 * Small gesture recognizer used by browser bindings and tests.
 * It intentionally only emits events; a larger chartplotter can decide how to apply them.
 */
class ChartInteractionController(
    private val hitTester: ChartHitTester = EmptyChartHitTester(),
    private val listener: ChartInteractionListener
) {
    private var downEvent: ChartPointerEvent? = null
    private var lastMove: ChartPointerEvent? = null

    fun handlePointer(event: ChartPointerEvent) {
        when (event.phase) {
            PointerPhase.Down -> {
                downEvent = event
                lastMove = event
            }
            PointerPhase.Move -> {
                val previous = lastMove
                if (previous != null) {
                    listener.onUserEvent(
                        ChartUserEvent.Drag(
                            from = previous.position,
                            to = event.position,
                            delta = ScreenDelta(event.position.x - previous.position.x, event.position.y - previous.position.y)
                        )
                    )
                }
                lastMove = event
            }
            PointerPhase.Up -> {
                val start = downEvent
                if (start != null && isClick(start.position, event.position)) {
                    val hits = hitTester.hitTest(event.position)
                    val userEvent = if (event.kind == PointerKind.Touch) {
                        ChartUserEvent.TouchObject(event.position, hits)
                    } else {
                        ChartUserEvent.ClickObject(event.position, hits)
                    }
                    listener.onUserEvent(userEvent)
                }
                downEvent = null
                lastMove = null
            }
            PointerPhase.Cancel -> {
                downEvent = null
                lastMove = null
            }
        }
    }

    fun handleWheel(event: ChartWheelEvent) {
        val direction = dominantScrollDirection(event.deltaX, event.deltaY)
        listener.onUserEvent(ChartUserEvent.Scroll(direction, ScreenDelta(event.deltaX, event.deltaY), event.position))
        if (abs(event.deltaY) > abs(event.deltaX)) {
            val factor = if (event.deltaY < 0.0) 1.2 else 1.0 / 1.2
            listener.onUserEvent(ChartUserEvent.Zoom(factor, event.position))
        }
    }

    fun queryCenterCrosshair(camera: ChartCameraState) {
        listener.onUserEvent(
            ChartUserEvent.CenterCrosshairQuery(
                ScreenPoint(camera.viewport.widthPx / 2.0, camera.viewport.heightPx / 2.0),
                hitTester.centerCrosshairHitTest(camera)
            )
        )
    }

    fun emitRotate(deltaDegrees: Double, focus: ScreenPoint) {
        listener.onUserEvent(ChartUserEvent.Rotate(deltaDegrees, focus))
    }

    fun emitTilt(deltaDegrees: Double, focus: ScreenPoint) {
        listener.onUserEvent(ChartUserEvent.Tilt(deltaDegrees, focus))
    }

    fun emitHold(point: ScreenPoint, durationMillis: Double) {
        listener.onUserEvent(ChartUserEvent.Hold(point, durationMillis))
    }

    private fun isClick(a: ScreenPoint, b: ScreenPoint): Boolean = abs(a.x - b.x) <= 4.0 && abs(a.y - b.y) <= 4.0

    private fun dominantScrollDirection(dx: Double, dy: Double): ScrollDirection = when {
        abs(dx) < 0.01 && abs(dy) < 0.01 -> ScrollDirection.None
        abs(dx) > abs(dy) && dx > 0.0 -> ScrollDirection.Right
        abs(dx) > abs(dy) -> ScrollDirection.Left
        dy > 0.0 -> ScrollDirection.Down
        else -> ScrollDirection.Up
    }
}
