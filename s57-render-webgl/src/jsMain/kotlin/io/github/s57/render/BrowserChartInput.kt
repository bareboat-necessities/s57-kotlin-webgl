package io.github.s57.render

import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.Event

/** Browser pointer/wheel binding. It emits library-level events but does not implement chartplotter UX. */
class BrowserChartInput(
    private val controller: ChartInteractionController,
    private val requireFocusForWheelZoom: Boolean = false,
    private val wheelZoomOnly: Boolean = false,
    private val invertWheelZoom: Boolean = false
) {
    private var wheelZoomArmed: Boolean = false

    fun attach(canvasId: String): Boolean {
        val canvas = document.getElementById(canvasId) as? HTMLCanvasElement ?: return false
        canvas.setAttribute("style", listOfNotNull(canvas.getAttribute("style"), "touch-action: none").joinToString("; "))
        canvas.setAttribute("tabindex", "0")

        canvas.addEventListener("pointerdown", { raw: Event ->
            wheelZoomArmed = true
            focusCanvas(canvas)
            capturePointer(canvas, pointerId(raw))
            controller.handlePointer(toChartPointerEvent(raw, PointerPhase.Down, canvas))
            raw.preventDefault()
        })
        canvas.addEventListener("pointermove", { raw: Event ->
            controller.handlePointer(toChartPointerEvent(raw, PointerPhase.Move, canvas))
            raw.preventDefault()
        })
        canvas.addEventListener("pointerup", { raw: Event ->
            controller.handlePointer(toChartPointerEvent(raw, PointerPhase.Up, canvas))
            raw.preventDefault()
        })
        canvas.addEventListener("pointercancel", { raw: Event ->
            controller.handlePointer(toChartPointerEvent(raw, PointerPhase.Cancel, canvas))
            raw.preventDefault()
        })
        canvas.addEventListener("blur", {
            wheelZoomArmed = false
        })
        canvas.addEventListener("wheel", { raw: Event ->
            if (requireFocusForWheelZoom && (!wheelZoomArmed || document.activeElement != canvas)) {
                return@addEventListener
            }
            val point = canvasPoint(raw, canvas)
            val deltaX = eventNumber(raw, "deltaX")
            val rawDeltaY = eventNumber(raw, "deltaY")
            val deltaY = if (invertWheelZoom) -rawDeltaY else rawDeltaY
            val wheelEvent = ChartWheelEvent(point, deltaX, deltaY, eventNumber(raw, "timeStamp"))
            if (wheelZoomOnly) controller.handleWheelZoom(wheelEvent) else controller.handleWheel(wheelEvent)
            raw.preventDefault()
        })
        return true
    }

    private fun toChartPointerEvent(event: Event, phase: PointerPhase, canvas: HTMLCanvasElement): ChartPointerEvent = ChartPointerEvent(
        pointerId = pointerId(event),
        phase = phase,
        position = canvasPoint(event, canvas),
        kind = when (eventString(event, "pointerType")) {
            "mouse" -> PointerKind.Mouse
            "touch" -> PointerKind.Touch
            "pen" -> PointerKind.Pen
            else -> PointerKind.Unknown
        },
        button = when (eventInteger(event, "button", -1)) {
            0 -> MouseButton.Primary
            1 -> MouseButton.Middle
            2 -> MouseButton.Secondary
            -1 -> MouseButton.None
            else -> MouseButton.Other
        },
        timestampMillis = eventNumber(event, "timeStamp")
    )

    private fun canvasPoint(event: Event, canvas: HTMLCanvasElement): ScreenPoint {
        val rect = canvas.getBoundingClientRect()
        return ScreenPoint(
            x = eventNumber(event, "clientX") - rect.left,
            y = eventNumber(event, "clientY") - rect.top
        )
    }

    private fun pointerId(event: Event): Int = eventInteger(event, "pointerId")

    private fun capturePointer(canvas: HTMLCanvasElement, pointerId: Int) {
        try {
            canvas.asDynamic().setPointerCapture(pointerId)
        } catch (_: Throwable) {
            // Some browsers throw for synthetic/mouse events that do not carry a capturable pointer id.
        }
    }

    private fun focusCanvas(canvas: HTMLCanvasElement) {
        try {
            canvas.asDynamic().focus()
        } catch (_: Throwable) {
            // Focusing is a UX helper, not required for pointer handling.
        }
    }

    private fun eventString(event: Event, propertyName: String): String {
        val value = event.asDynamic()[propertyName]
        return value as? String ?: ""
    }

    private fun eventNumber(event: Event, propertyName: String, fallback: Double = 0.0): Double {
        val value = event.asDynamic()[propertyName]
        return numberValue(value, fallback)
    }

    private fun eventInteger(event: Event, propertyName: String, fallback: Int = 0): Int {
        val value = event.asDynamic()[propertyName]
        return integerValue(value, fallback)
    }

    private fun numberValue(value: Any?, fallback: Double = 0.0): Double = when (value) {
        is Double -> value
        is Float -> value.toDouble()
        is Int -> value.toDouble()
        is Long -> value.toDouble()
        else -> fallback
    }

    private fun integerValue(value: Any?, fallback: Int = 0): Int = when (value) {
        is Int -> value
        is Double -> value.toInt()
        is Float -> value.toInt()
        is Long -> value.toInt()
        else -> fallback
    }
}
