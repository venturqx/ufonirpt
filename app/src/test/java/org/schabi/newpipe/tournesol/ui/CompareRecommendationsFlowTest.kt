package org.schabi.newpipe.tournesol.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.tournesol.SessionExpiredException

class CompareRecommendationsFlowTest {
    private val rig = CompareRig()

    private val loading = RecommendationsView(visible = true, loading = true, error = null, itemCount = 0, totalCount = null)
    private val idle = loading.copy(loading = false)
    private val oneComparison = """{"count":7,"results":[{"entity_a":{"uid":"$UID_A"},"entity_b":{"uid":"$UID_B"}}]}"""

    @After
    fun tearDown() {
        rig.close()
    }

    @Test
    fun `opening shows a spinner and asks for twenty of my comparisons`() {
        rig.openRecommendations()

        assertEquals(loading, rig.recommendations)
        assertEquals(listOf(COMPARISONS), rig.server.calls)
        assertEquals("20", rig.server.lastQuery("limit"))
    }

    @Test
    fun `a result fills the dialog and caches the count`() {
        rig.server.reply(COMPARISONS, 200, oneComparison)
        rig.openRecommendations()

        rig.deliver()

        assertEquals(idle.copy(itemCount = 1, totalCount = 7), rig.recommendations)
        assertEquals(7, rig.comparisonCount)
    }

    @Test
    fun `an empty result is reported as an error and keeps the count`() {
        rig.server.reply(COMPARISONS, 200, """{"count":3,"results":[]}""")
        rig.openRecommendations()

        rig.deliver()

        assertEquals(
            idle.copy(error = message(R.string.compare_no_comparisons_available), totalCount = 3),
            rig.recommendations
        )
        assertEquals(3, rig.comparisonCount)
    }

    @Test
    fun `a result without a count leaves the cached count alone`() {
        rig.comparisonCount = 9
        rig.server.reply(COMPARISONS, 200, """{"results":[]}""")
        rig.openRecommendations()

        rig.deliver()

        assertEquals(9, rig.comparisonCount)
        assertNull(rig.recommendations.totalCount)
    }

    @Test
    fun `no token reports login required and opens the login dialog`() {
        rig.token = null

        rig.openRecommendations()
        rig.deliver()

        assertEquals(idle.copy(error = message(R.string.compare_login_required)), rig.recommendations)
        assertTrue(rig.login.visible)
    }

    @Test
    fun `a rejected refresh token still reports login required`() {
        rig.tokenFailure = SessionExpiredException()

        rig.openRecommendations()
        rig.deliver()

        assertEquals(idle.copy(error = message(R.string.compare_login_required)), rig.recommendations)
        assertTrue(rig.login.visible)
    }

    @Test
    fun `an expired session is an ordinary failure here, unlike submitting`() {
        rig.server.reply(COMPARISONS, 401)

        rig.openRecommendations()
        rig.deliver()

        assertEquals(idle.copy(error = message(R.string.compare_failed)), rig.recommendations)
        assertFalse(rig.login.visible)
    }

    @Test
    fun `a network failure shows a fixed message and logs the detail`() {
        rig.server.failNetwork(COMPARISONS, "Unable to resolve host")

        rig.openRecommendations()
        rig.deliver()

        assertEquals(idle.copy(error = message(R.string.network_error)), rig.recommendations)
        assertEquals(listOf("Unable to resolve host"), rig.loggedFailures.map { it.message })
    }

    @Test
    fun `dismissing hides the dialog but lets the request finish`() {
        rig.server.reply(COMPARISONS, 200, oneComparison)
        rig.openRecommendations()

        rig.dismissRecommendations()
        assertFalse(rig.recommendations.visible)
        rig.deliver()

        assertFalse(rig.recommendations.visible)
        assertEquals(7, rig.comparisonCount)
    }

    @Test
    fun `reopening replaces the running request and clears old content`() {
        rig.server.reply(COMPARISONS, 500)
        rig.openRecommendations()
        rig.deliver()

        rig.server.reply(COMPARISONS, 200, oneComparison)
        rig.openRecommendations()

        assertEquals(loading, rig.recommendations)
        rig.deliver()
        assertEquals(idle.copy(itemCount = 1, totalCount = 7), rig.recommendations)
    }

    @Test
    fun `signing out cancels the request and leaves an idle dialog`() {
        rig.server.reply(COMPARISONS, 200, oneComparison)
        rig.openRecommendations()

        rig.signedOut()
        rig.deliver()

        assertEquals(idle, rig.recommendations)
        assertNull(rig.comparisonCount)
    }

    @Test
    fun `signing out leaves already loaded comparisons in place`() {
        rig.server.reply(COMPARISONS, 200, oneComparison)
        rig.openRecommendations()
        rig.deliver()

        rig.signedOut()
        rig.deliver()

        assertEquals(idle.copy(itemCount = 1, totalCount = 7), rig.recommendations)
    }

    @Test
    fun `destroying the view drops the list and the count`() {
        rig.server.reply(COMPARISONS, 200, oneComparison)
        rig.openRecommendations()
        rig.deliver()

        rig.destroyView()

        assertEquals(idle, rig.recommendations)
    }

    @Test
    fun `destroying the view keeps an error but drops the count`() {
        rig.server.reply(COMPARISONS, 200, """{"count":3,"results":[]}""")
        rig.openRecommendations()
        rig.deliver()

        rig.destroyView()

        assertEquals(idle.copy(error = message(R.string.compare_no_comparisons_available)), rig.recommendations)
    }
}
