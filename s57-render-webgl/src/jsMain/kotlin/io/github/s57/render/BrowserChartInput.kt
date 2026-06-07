package io.github.s57.render

import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.Event

/** Browser pointer/wheel binding. It emits library-level events but does not implement chartplotter UX. */
class BrowserChartInput(
    private val controller: ChartInteractionController
) {
    fun attach(canvasId: String): Boolean {
        val canvas = document.getElementById(canvasId) as? HTMLCanvasElement ?: return false
        canvas.asDynamic().style.touchAction = "none"

        canvas.addEventListener("pointerdown", { raw: Event ->
            val event = raw.asDynamic()
            canvas.asDynamic().setPointerCapture(event.pointerId)
            controller.handlePointer(toChartPointerEvent(event, PointerPhase.Down, canvas))
            raw.preventDefault()
        })
        canvas.addEventListener("pointermove", { raw: Event ->
            val event = raw.asDynamic()
            controller.handlePointer(toChartPointerEvent(event, PointerPhase.Move, canvas))
            raw.preventDefault()
        })
        canvas.addEventListener("pointerup", { raw: Event ->
            val event = raw.asDynamic()
            controller.handlePointer(toChartPointerEvent(event, PointerPhase.Up, canvas))
            raw.preventDefault()
        })
        canvas.addEventListener("pointercancel", { raw: Event ->
            val event = raw.asDynamic()
            controller.handlePointer(toChartPointerEvent(event, PointerPhase.Cancel, canvas))
            raw.preventDefault()
        })
        canvas.asDynamic().addEventListener("wheel", { raw: Event ->
            val event = raw.asDynamic()
            val point = canvasPoint(event, canvas)
            controller.handleWheel(ChartWheelEvent(point, number(event.deltaX), number(event.deltaY), number(event.timeStamp)))
            raw.preventDefault()
        }, js("({ passive: false })"))
        return true
    }

    private fun toChartPointerEvent(event: dynamic, phase: PointerPhase, canvas: HTMLCanvasElement): ChartPointerEvent = ChartPointerEvent(
        pointerId = integer(event.pointerId),
        phase = phase,
        position = canvasPoint(event, canvas),
        kind = when ((event.pointerType as? String).orEmpty()) {
            "mouse" -> PointerKind.Mouse
            "touch" -> PointerKind.Touch
            "pen" -> PointerKind.Pen
            else -> PointerKind.Unknown
        },
        button = when (integer(event.button, -1)) {
            0 -> MouseButton.Primary
            1 -> MouseButton.Middle
            2 -> MouseButton.Secondary
            -1 -> MouseButton.None
            else -> MouseButton.Other
        },
        timestampMillis = number(event.timeStamp)
    )

    private fun canvasPoint(event: dynamic, canvas: HTMLCanvasElement): ScreenPoint {
        val rect = canvas.getBoundingClientRect()
        return ScreenPoint(
            x = number(event.clientX) - rect.left,
            y = number(event.clientY) - rect.top
        )
    }

    private fun number(value: dynamic, fallback: Double = 0.0): Double = when (value) {
        is Double -> value
        is Float -> value.toDouble()
        is Int -> value.toDouble()
        is Long -> value.toDouble()
        else -> fallback
    }

    private fun integer(value: dynamic, fallback: Int = 0): Int = when (value) {
        is Int -> value
        is Double -> value.toInt()
        is Float -> value.toInt()
        is Long -> value.toInt()
        else -> fallback
    }
}
