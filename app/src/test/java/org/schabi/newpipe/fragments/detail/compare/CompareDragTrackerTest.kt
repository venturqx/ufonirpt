package org.schabi.newpipe.fragments.detail.compare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.fragments.detail.SCORE_MAX
import org.schabi.newpipe.fragments.detail.SCORE_MIN

class CompareDragTrackerTest {
    private val noActions = emptyList<DragAction>()

    private fun tracker(
        index: Int = 5,
        value: Int = 0,
        horizontalSensitivity: Float = 1f,
        verticalSensitivity: Float = 1f,
        maxIndex: () -> Int = { 10 }
    ) = CompareDragTracker(
        pxPerScore = 2f,
        verticalStepPx = 100f,
        horizontalSensitivity = horizontalSensitivity,
        verticalSensitivity = verticalSensitivity,
        scoreMin = SCORE_MIN,
        scoreMax = SCORE_MAX,
        maxIndex = maxIndex
    ).apply { start(index, value) }

    @Test
    fun `first event with more x locks horizontal and acts in order`() {
        val actions = tracker().onDrag(10f, 1f)

        assertEquals(
            listOf(DragAction.MarkSelected, DragAction.ConsumePointer, DragAction.SetScore(5)),
            actions
        )
    }

    @Test
    fun `first event with more y locks vertical and leaves the pointer unconsumed`() {
        assertEquals(noActions, tracker().onDrag(1f, 10f))
    }

    @Test
    fun `tie on the first event goes horizontal`() {
        val actions = tracker().onDrag(10f, 10f)

        assertEquals(
            listOf(DragAction.MarkSelected, DragAction.ConsumePointer, DragAction.SetScore(5)),
            actions
        )
    }

    @Test
    fun `zero movement on the first event goes horizontal`() {
        assertEquals(
            listOf(DragAction.MarkSelected, DragAction.ConsumePointer),
            tracker().onDrag(0f, 0f)
        )
    }

    @Test
    fun `horizontal lock ignores later vertical movement`() {
        val tracker = tracker()
        tracker.onDrag(10f, 0f)

        assertEquals(listOf(DragAction.ConsumePointer), tracker.onDrag(0f, 500f))
    }

    @Test
    fun `vertical lock ignores later horizontal movement`() {
        val tracker = tracker()
        tracker.onDrag(0f, 10f)

        assertEquals(noActions, tracker.onDrag(500f, 0f))
    }

    @Test
    fun `horizontal steps truncate and keep the remainder`() {
        val tracker = tracker()

        assertEquals(
            listOf(DragAction.MarkSelected, DragAction.ConsumePointer, DragAction.SetScore(2)),
            tracker.onDrag(5f, 0f)
        )
        assertEquals(
            listOf(DragAction.ConsumePointer, DragAction.SetScore(3)),
            tracker.onDrag(1f, 0f)
        )
    }

    @Test
    fun `negative horizontal steps truncate toward zero and keep the remainder`() {
        val tracker = tracker()

        assertEquals(
            listOf(DragAction.MarkSelected, DragAction.ConsumePointer, DragAction.SetScore(-2)),
            tracker.onDrag(-5f, 0f)
        )
        assertEquals(
            listOf(DragAction.ConsumePointer, DragAction.SetScore(-3)),
            tracker.onDrag(-1f, 0f)
        )
    }

    @Test
    fun `horizontal movement below one step consumes without setting a score`() {
        val tracker = tracker()

        assertEquals(
            listOf(DragAction.MarkSelected, DragAction.ConsumePointer),
            tracker.onDrag(1f, 0f)
        )
        assertEquals(
            listOf(DragAction.ConsumePointer, DragAction.SetScore(1)),
            tracker.onDrag(1f, 0f)
        )
    }

    @Test
    fun `horizontal sensitivity scales the movement`() {
        val actions = tracker(horizontalSensitivity = 1.5f).onDrag(4f, 0f)

        assertEquals(DragAction.SetScore(3), actions.last())
    }

    @Test
    fun `score clamps at the upper bound`() {
        val actions = tracker(value = SCORE_MAX - 1).onDrag(10f, 0f)

        assertEquals(DragAction.SetScore(SCORE_MAX), actions.last())
    }

    @Test
    fun `score clamps at the lower bound`() {
        val actions = tracker(value = SCORE_MIN + 1).onDrag(-10f, 0f)

        assertEquals(DragAction.SetScore(SCORE_MIN), actions.last())
    }

    @Test
    fun `movement past a score bound still sets the score and is not banked`() {
        val tracker = tracker(value = SCORE_MAX)

        assertEquals(DragAction.SetScore(SCORE_MAX), tracker.onDrag(20f, 0f).last())
        assertEquals(DragAction.SetScore(SCORE_MAX - 1), tracker.onDrag(-2f, 0f).last())
    }

    @Test
    fun `vertical step moves down then consumes`() {
        assertEquals(
            listOf(DragAction.SetIndex(6), DragAction.ConsumePointer),
            tracker().onDrag(0f, 100f)
        )
    }

    @Test
    fun `vertical step moves up then consumes`() {
        assertEquals(
            listOf(DragAction.SetIndex(4), DragAction.ConsumePointer),
            tracker().onDrag(0f, -100f)
        )
    }

    @Test
    fun `one event can step several times and consumes once after them`() {
        val tracker = tracker()

        assertEquals(
            listOf(DragAction.SetIndex(6), DragAction.SetIndex(7), DragAction.ConsumePointer),
            tracker.onDrag(0f, 250f)
        )
        assertEquals(
            listOf(DragAction.SetIndex(8), DragAction.ConsumePointer),
            tracker.onDrag(0f, 50f)
        )
    }

    @Test
    fun `vertical movement accumulates across events`() {
        val tracker = tracker()

        assertEquals(noActions, tracker.onDrag(0f, 60f))
        assertEquals(
            listOf(DragAction.SetIndex(6), DragAction.ConsumePointer),
            tracker.onDrag(0f, 40f)
        )
    }

    @Test
    fun `vertical sensitivity scales the movement`() {
        assertEquals(
            listOf(DragAction.SetIndex(6), DragAction.ConsumePointer),
            tracker(verticalSensitivity = 2f).onDrag(0f, 50f)
        )
    }

    @Test
    fun `vertical step at the last index stays unconsumed`() {
        assertEquals(noActions, tracker(index = 10).onDrag(0f, 100f))
    }

    @Test
    fun `vertical step at the first index stays unconsumed`() {
        assertEquals(noActions, tracker(index = 0).onDrag(0f, -100f))
    }

    @Test
    fun `steps past the last index stop at the bound`() {
        assertEquals(
            listOf(DragAction.SetIndex(10), DragAction.ConsumePointer),
            tracker(index = 9).onDrag(0f, 300f)
        )
    }

    @Test
    fun `movement past an index bound is not banked`() {
        val tracker = tracker(index = 10)

        assertEquals(noActions, tracker.onDrag(0f, 250f))
        assertEquals(
            listOf(DragAction.SetIndex(9), DragAction.ConsumePointer),
            tracker.onDrag(0f, -150f)
        )
    }

    @Test
    fun `max index is read on every step`() {
        var maxIndex = 5
        val tracker = tracker(index = 5, maxIndex = { maxIndex })

        assertEquals(noActions, tracker.onDrag(0f, 100f))
        maxIndex = 6
        assertEquals(
            listOf(DragAction.SetIndex(6), DragAction.ConsumePointer),
            tracker.onDrag(0f, 100f)
        )
    }

    @Test
    fun `short downward release steps once`() {
        val tracker = tracker()
        tracker.onDrag(0f, 10f)

        assertEquals(DragAction.SetIndex(6), tracker.onEnd())
    }

    @Test
    fun `short upward release steps once`() {
        val tracker = tracker()
        tracker.onDrag(0f, -10f)

        assertEquals(DragAction.SetIndex(4), tracker.onEnd())
    }

    @Test
    fun `short release follows the net direction`() {
        val tracker = tracker()
        tracker.onDrag(0f, 10f)
        tracker.onDrag(0f, -30f)

        assertEquals(DragAction.SetIndex(4), tracker.onEnd())
    }

    @Test
    fun `short release at the last index does nothing`() {
        val tracker = tracker(index = 10)
        tracker.onDrag(0f, 10f)

        assertNull(tracker.onEnd())
    }

    @Test
    fun `short release at the first index does nothing`() {
        val tracker = tracker(index = 0)
        tracker.onDrag(0f, -10f)

        assertNull(tracker.onEnd())
    }

    @Test
    fun `release after a real step does not step again`() {
        val tracker = tracker(verticalSensitivity = 2f)
        tracker.onDrag(0f, 50f)

        assertNull(tracker.onEnd())
    }

    @Test
    fun `release at or beyond one raw step does not step`() {
        val tracker = tracker(verticalSensitivity = 0.5f)
        tracker.onDrag(0f, 100f)

        assertNull(tracker.onEnd())
    }

    @Test
    fun `release with more sideways than vertical travel does not step`() {
        val tracker = tracker()
        tracker.onDrag(0f, 10f)
        tracker.onDrag(20f, 0f)

        assertNull(tracker.onEnd())
    }

    @Test
    fun `release with equal sideways and vertical travel does not step`() {
        val tracker = tracker()
        tracker.onDrag(0f, 10f)
        tracker.onDrag(10f, 0f)

        assertNull(tracker.onEnd())
    }

    @Test
    fun `release with no net vertical travel does not step`() {
        val tracker = tracker()
        tracker.onDrag(0f, 10f)
        tracker.onDrag(0f, -10f)

        assertNull(tracker.onEnd())
    }

    @Test
    fun `release of a horizontal drag does not step`() {
        val tracker = tracker()
        tracker.onDrag(10f, 0f)

        assertNull(tracker.onEnd())
    }

    @Test
    fun `release without movement does not step`() {
        assertNull(tracker().onEnd())
    }

    @Test
    fun `cancel drops the pending fallback step`() {
        val tracker = tracker()
        tracker.onDrag(0f, 10f)

        tracker.cancel()

        assertNull(tracker.onEnd())
    }

    @Test
    fun `cancel clears accumulated vertical movement`() {
        val tracker = tracker()
        tracker.onDrag(0f, 60f)

        tracker.cancel()

        assertEquals(noActions, tracker.onDrag(0f, 60f))
    }

    @Test
    fun `cancel clears the axis lock`() {
        val tracker = tracker()
        tracker.onDrag(10f, 0f)

        tracker.cancel()

        assertEquals(noActions, tracker.onDrag(0f, 10f))
    }

    @Test
    fun `new drag after end forgets the horizontal remainder`() {
        val tracker = tracker()
        tracker.onDrag(1f, 0f)
        tracker.onEnd()

        tracker.start(5, 0)

        assertEquals(
            listOf(DragAction.MarkSelected, DragAction.ConsumePointer),
            tracker.onDrag(1f, 0f)
        )
    }

    @Test
    fun `new drag after end uses the new index and value`() {
        val tracker = tracker()
        tracker.onDrag(10f, 0f)
        tracker.onEnd()

        tracker.start(2, 40)

        assertEquals(DragAction.SetScore(41), tracker.onDrag(2f, 0f).last())
        tracker.onEnd()
        tracker.start(2, 40)
        tracker.onDrag(0f, 10f)
        assertEquals(DragAction.SetIndex(3), tracker.onEnd())
    }

    @Test
    fun `new drag after end can lock the other axis`() {
        val tracker = tracker()
        tracker.onDrag(10f, 0f)
        tracker.onEnd()

        tracker.start(5, 0)

        assertEquals(noActions, tracker.onDrag(0f, 10f))
    }

    @Test
    fun `new drag after a real step can use the fallback again`() {
        val tracker = tracker(verticalSensitivity = 2f)
        tracker.onDrag(0f, 50f)
        tracker.onEnd()

        tracker.start(6, 0)
        tracker.onDrag(0f, 10f)

        assertEquals(DragAction.SetIndex(7), tracker.onEnd())
    }

    @Test
    fun `mark selected fires once per horizontal drag`() {
        val tracker = tracker()

        val actions = listOf(10f, 10f, -10f, 3f).flatMap { tracker.onDrag(it, 0f) }

        assertEquals(1, actions.count { it == DragAction.MarkSelected })
        assertEquals(DragAction.MarkSelected, actions.first())
    }

    @Test
    fun `mark selected fires again on the next drag`() {
        val tracker = tracker()
        tracker.onDrag(10f, 0f)
        tracker.onEnd()

        tracker.start(5, 0)

        assertEquals(DragAction.MarkSelected, tracker.onDrag(10f, 0f).first())
    }

    @Test
    fun `mark selected never fires for a vertical drag`() {
        val tracker = tracker()

        val actions = tracker.onDrag(0f, 100f) + tracker.onDrag(500f, 0f) + tracker.onDrag(0f, -100f)

        assertEquals(0, actions.count { it == DragAction.MarkSelected })
    }
}
