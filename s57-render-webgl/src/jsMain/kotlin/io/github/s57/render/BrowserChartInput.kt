package io.github.s57.render

import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement

/** Browser pointer/wheel binding. It emits library-level events but does not implement chartplotter UX. */
class BrowserChartInput(
    private val controller: ChartInteractionController
) {
    fun attach(canvasId: String): Boolean {
        val canvas = document.getElementById(canvasId) as? HTMLCanvasElement ?: return false
        canvas.asDynamic().style.touchAction = "none"

        canvas.asDynamic().addEventListener("pointerdown", { event: dynamic ->
            canvas.asDynamic().setPointerCapture(event.pointerId)
            controller.handlePointer(event.toChartPointerEvent(PointerPhase.Down, canvas))
            event.preventDefault()
        })
        canvas.asDynamic().addEventListener("pointermove", { event: dynamic ->
            controller.handlePointer(event.toChartPointerEvent(PointerPhase.Move, canvas))
            event.preventDefault()
        })
        canvas.asDynamic().addEventListener("pointerup", { event: dynamic ->
            controller.handlePointer(event.toChartPointerEvent(PointerPhase.Up, canvas))
            event.preventDefault()
        })
        canvas.asDynamic().addEventListener("pointercancel", { event: dynamic ->
            controller.handlePointer(event.toChartPointerEvent(PointerPhase.Cancel, canvas))
            event.preventDefault()
        })
        canvas.asDynamic().addEventListener("wheel", { event: dynamic ->
            val point = event.canvasPoint(canvas)
            controller.handleWheel(ChartWheelEvent(point, event.deltaX as Double, event.deltaY as Double, event.timeStamp as Double))
            event.preventDefault()
        }, js("({ passive: false })"))
        return true
    }

    private fun dynamic.toChartPointerEvent(phase: PointerPhase, canvas: HTMLCanvasElement): ChartPointerEvent = ChartPointerEvent(
        pointerId = ((this.pointerId as? Int) ?: 0),
        phase = phase,
        position = this.canvasPoint(canvas),
        kind = when ((this.pointerType as? String).orEmpty()) {
            "mouse" -> PointerKind.Mouse
            "touch" -> PointerKind.Touch
            "pen" -> PointerKind.Pen
            else -> PointerKind.Unknown
        },
        button = when ((this.button as? Int) ?: -1) {
            0 -> MouseButton.Primary
            1 -> MouseButton.Middle
            2 -> MouseButton.Secondary
            -1 -> MouseButton.None
            else -> MouseButton.Other
        },
        timestampMillis = ((this.timeStamp as? Double) ?: 0.0)
    )

    private fun dynamic.canvasPoint(canvas: HTMLCanvasElement): ScreenPoint {
        val rect = canvas.getBoundingClientRect()
        return ScreenPoint(
            x = ((this.clientX as? Double) ?: 0.0) - rect.left,
            y = ((this.clientY as? Double) ?: 0.0) - rect.top
        )
    }
}
