package com.vphone.client

import android.view.MotionEvent
import com.vphone.client.stream.StreamController

/**
 * Translates Android multi-touch MotionEvents into vphone input calls.
 *
 * One finger maps to `input.touch` (vphone's single-point digitizer). Two
 * fingers map to `input.touch2` (vphone's dedicated two-finger path, needed
 * because `input.touch` carries only one point and cannot express a pinch).
 *
 * When the finger count changes mid-gesture, the active mode is closed with an
 * "up" event before the new mode begins, so the two injection paths never
 * overlap (a leaked "down" would leave a phantom finger stuck on the guest).
 */
class TouchInjector {

    private enum class Mode { NONE, ONE, TWO }

    private var mode = Mode.NONE

    /** pointerId -> [x, y] normalized 0..1, insertion-ordered, at most 2 entries. */
    private val pointers = LinkedHashMap<Int, FloatArray>()

    private var lastMove = 0L

    fun handle(controller: StreamController?, event: MotionEvent, viewWidth: Int, viewHeight: Int): Boolean {
        val c = controller ?: return false
        if (viewWidth <= 0 || viewHeight <= 0) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointers.clear()
                addPointer(event, 0, viewWidth, viewHeight)
                mode = Mode.ONE
                pointers.values.first().let { c.sendTouch("down", it[0], it[1]) }
                lastMove = System.currentTimeMillis()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (pointers.size < 2) {
                    addPointer(event, event.actionIndex, viewWidth, viewHeight)
                    if (mode == Mode.ONE && pointers.size >= 2) {
                        val one = pointers.values.first()
                        c.sendTouch("up", one[0], one[1])
                        mode = Mode.TWO
                        sendTwo(c, "down")
                    }
                }
                // A third finger is ignored.
            }

            MotionEvent.ACTION_MOVE -> {
                updatePointers(event, viewWidth, viewHeight)
                val now = System.currentTimeMillis()
                if (now - lastMove < MOVE_THROTTLE_MS) return true
                lastMove = now
                when (mode) {
                    Mode.ONE -> pointers.values.firstOrNull()?.let { c.sendTouch("move", it[0], it[1]) }
                    Mode.TWO -> if (pointers.size >= 2) sendTwo(c, "move")
                    Mode.NONE -> Unit
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val liftedId = event.getPointerId(event.actionIndex)
                val before = pointers.values.toList()
                pointers.remove(liftedId)
                when (mode) {
                    Mode.TWO -> {
                        if (before.size >= 2) {
                            c.sendTouch2("up", before[0][0], before[0][1], before[1][0], before[1][1])
                        }
                        val remaining = pointers.values.firstOrNull()
                        if (remaining != null) {
                            mode = Mode.ONE
                            c.sendTouch("down", remaining[0], remaining[1])
                        } else {
                            mode = Mode.NONE
                        }
                    }
                    Mode.ONE -> {
                        before.firstOrNull()?.let { c.sendTouch("up", it[0], it[1]) }
                        mode = Mode.NONE
                    }
                    Mode.NONE -> Unit
                }
            }

            MotionEvent.ACTION_UP -> {
                endGesture(c)
            }

            MotionEvent.ACTION_CANCEL -> {
                endGesture(c)
            }
        }
        return true
    }

    private fun endGesture(c: StreamController) {
        val before = pointers.values.toList()
        when (mode) {
            Mode.ONE -> before.firstOrNull()?.let { c.sendTouch("up", it[0], it[1]) }
            Mode.TWO -> if (before.size >= 2) {
                c.sendTouch2("up", before[0][0], before[0][1], before[1][0], before[1][1])
            }
            Mode.NONE -> Unit
        }
        pointers.clear()
        mode = Mode.NONE
    }

    private fun sendTwo(c: StreamController, phase: String) {
        val values = pointers.values.toList()
        if (values.size < 2) return
        c.sendTouch2(phase, values[0][0], values[0][1], values[1][0], values[1][1])
    }

    private fun addPointer(event: MotionEvent, index: Int, viewWidth: Int, viewHeight: Int) {
        val id = event.getPointerId(index)
        pointers[id] = floatArrayOf(
            (event.getX(index) / viewWidth.toFloat()).coerceIn(0f, 1f),
            (event.getY(index) / viewHeight.toFloat()).coerceIn(0f, 1f),
        )
    }

    private fun updatePointers(event: MotionEvent, viewWidth: Int, viewHeight: Int) {
        for (i in 0 until event.pointerCount) {
            val id = event.getPointerId(i)
            if (pointers.containsKey(id)) {
                pointers[id] = floatArrayOf(
                    (event.getX(i) / viewWidth.toFloat()).coerceIn(0f, 1f),
                    (event.getY(i) / viewHeight.toFloat()).coerceIn(0f, 1f),
                )
            }
        }
    }

    companion object {
        private const val MOVE_THROTTLE_MS = 8L
    }
}
