package org.schabi.newpipe.views

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.views.ScoreCardLoad.Fetch

private val ALL_FETCHES = setOf(Fetch.INFO, Fetch.DISTRIBUTION)

class ScoreCardLoadTest {
    private val load = ScoreCardLoad()

    private fun assertNoRetry() {
        assertEquals(emptySet<Fetch>(), load.onAttached())
    }

    @Test
    fun `attach before bind retries nothing and bind starts every fetch`() {
        assertNoRetry()

        assertEquals(ALL_FETCHES, load.onBind())
    }

    @Test
    fun `attach after bind does not fetch twice`() {
        load.onBind()

        assertNoRetry()
    }

    @Test
    fun `detach mid load retries every unfinished fetch on reattach`() {
        load.onBind()
        load.onDetached()

        assertEquals(ALL_FETCHES, load.onAttached())
    }

    @Test
    fun `reattach retries only the unfinished fetch`() {
        load.onBind()
        load.onFinished(Fetch.INFO)
        load.onDetached()

        assertEquals(setOf(Fetch.DISTRIBUTION), load.onAttached())
    }

    @Test
    fun `retry happens once per detach`() {
        load.onBind()
        load.onDetached()
        load.onAttached()

        assertNoRetry()
    }

    @Test
    fun `detach during a retry retries again`() {
        load.onBind()
        load.onDetached()
        load.onAttached()
        load.onDetached()

        assertEquals(ALL_FETCHES, load.onAttached())
    }

    @Test
    fun `nothing is retried once every fetch finished`() {
        load.onBind()
        load.onFinished(Fetch.INFO)
        load.onFinished(Fetch.DISTRIBUTION)
        load.onDetached()

        assertNoRetry()
    }

    @Test
    fun `detach before any bind retries nothing`() {
        load.onDetached()

        assertNoRetry()
    }

    @Test
    fun `rebind restarts every fetch after completion`() {
        load.onBind()
        load.onFinished(Fetch.INFO)
        load.onFinished(Fetch.DISTRIBUTION)

        assertEquals(ALL_FETCHES, load.onBind())
    }

    @Test
    fun `rebind while detached does not fetch twice on attach`() {
        load.onBind()
        load.onDetached()
        load.onBind()

        assertNoRetry()
    }

    @Test
    fun `detach after rebind retries every fetch`() {
        load.onBind()
        load.onFinished(Fetch.INFO)
        load.onBind()
        load.onDetached()

        assertEquals(ALL_FETCHES, load.onAttached())
    }
}
