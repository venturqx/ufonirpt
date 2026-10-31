package org.schabi.newpipe.fragments.detail.compare

import kotlin.math.abs

internal sealed interface DragAction {
    data object MarkSelected : DragAction
    data object ConsumePointer : DragAction
    data class SetScore(val score: Int) : DragAction
    data class SetIndex(val index: Int) : DragAction
}

internal class CompareDragTracker(
    private val pxPerScore: Float,
    private val verticalStepPx: Float,
    private val horizontalSensitivity: Float,
    private val verticalSensitivity: Float,
    private val scoreMin: Int,
    private val scoreMax: Int,
    private val maxIndex: () -> Int
) {
    private var dragAxis: DragAxis? = null
    private var accumulatedX = 0f
    private var accumulatedY = 0f
    private var totalDragX = 0f
    private var totalDragY = 0f
    private var verticalStepTriggered = false
    private var currentIndex = 0
    private var currentValue = 0
    private var markedSelected = false

    fun start(index: Int, value: Int) {
        clearMotion()
        currentIndex = index
        currentValue = value
        markedSelected = false
    }

    fun onDrag(dx: Float, dy: Float): List<DragAction> {
        totalDragX += dx
        totalDragY += dy
        val actions = mutableListOf<DragAction>()
        val axis = dragAxis ?: lockAxis(dx, dy, actions)
        when (axis) {
            DragAxis.HORIZONTAL -> stepScore(dx, actions)
            DragAxis.VERTICAL -> stepIndex(dy, actions)
        }
        return actions
    }

    fun onEnd(): DragAction.SetIndex? {
        val fallback = fallbackStep()
        clearMotion()
        return fallback
    }

    fun cancel() {
        clearMotion()
    }

    private fun lockAxis(dx: Float, dy: Float, actions: MutableList<DragAction>): DragAxis {
        val axis = if (abs(dx) >= abs(dy)) DragAxis.HORIZONTAL else DragAxis.VERTICAL
        dragAxis = axis
        if (axis == DragAxis.HORIZONTAL) {
            markSelectedOnce(actions)
        }
        return axis
    }

    private fun stepScore(dx: Float, actions: MutableList<DragAction>) {
        // Horizontal drags belong to the criteria slider — consume them
        // so the parent pager/scroll never steals them.
        actions += DragAction.ConsumePointer
        accumulatedX += dx * horizontalSensitivity
        val steps = (accumulatedX / pxPerScore).toInt()
        if (steps != 0) {
            currentValue = (currentValue + steps).coerceIn(scoreMin, scoreMax)
            actions += DragAction.SetScore(currentValue)
            markSelectedOnce(actions)
            accumulatedX -= steps * pxPerScore
        }
    }

    private fun stepIndex(dy: Float, actions: MutableList<DragAction>) {
        accumulatedY += dy * verticalSensitivity
        var consumedStep = false
        while (abs(accumulatedY) >= verticalStepPx) {
            val step = if (accumulatedY > 0f) 1 else -1
            val nextIndex = (currentIndex + step).coerceIn(0, maxIndex())
            if (nextIndex != currentIndex) {
                currentIndex = nextIndex
                actions += DragAction.SetIndex(currentIndex)
                verticalStepTriggered = true
                consumedStep = true
            }
            accumulatedY -= step * verticalStepPx
        }
        // Only consume when we actually stepped criteria selection.
        // Otherwise leave the change unconsumed so the page's
        // verticalScroll / nested scroll can take over.
        if (consumedStep) {
            actions += DragAction.ConsumePointer
        }
    }

    private fun fallbackStep(): DragAction.SetIndex? {
        val isShortVerticalDrag = dragAxis == DragAxis.VERTICAL &&
            !verticalStepTriggered &&
            abs(totalDragY) > abs(totalDragX) &&
            abs(totalDragY) > 0f &&
            abs(totalDragY) < verticalStepPx
        if (!isShortVerticalDrag) {
            return null
        }
        val step = if (totalDragY > 0f) 1 else -1
        val fallbackIndex = (currentIndex + step).coerceIn(0, maxIndex())
        return if (fallbackIndex != currentIndex) DragAction.SetIndex(fallbackIndex) else null
    }

    private fun markSelectedOnce(actions: MutableList<DragAction>) {
        if (!markedSelected) {
            actions += DragAction.MarkSelected
            markedSelected = true
        }
    }

    private fun clearMotion() {
        dragAxis = null
        accumulatedX = 0f
        accumulatedY = 0f
        totalDragX = 0f
        totalDragY = 0f
        verticalStepTriggered = false
    }
}
