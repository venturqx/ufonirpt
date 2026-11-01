package org.schabi.newpipe.tournesol.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.R

class CompareLoginFlowTest {
    private val rig = CompareRig()

    private val hidden = LoginView(visible = false, inProgress = false, error = null)
    private val open = LoginView(visible = true, inProgress = false, error = null)
    private val submitting = LoginView(visible = true, inProgress = true, error = null)

    @After
    fun tearDown() {
        rig.close()
    }

    @Test
    fun `the dialog starts hidden and opens clean`() {
        assertEquals(hidden, rig.login)

        rig.showLogin()

        assertEquals(open, rig.login)
    }

    @Test
    fun `blank credentials are refused without a request`() {
        rig.showLogin()

        rig.submitLogin(" ", "secret")

        assertEquals(open.copy(error = message(R.string.tournesol_login_missing_fields)), rig.login)
        assertEquals(emptyList<String>(), rig.server.calls)
    }

    @Test
    fun `a blank password is refused too`() {
        rig.showLogin()

        rig.submitLogin("name", "")

        assertEquals(open.copy(error = message(R.string.tournesol_login_missing_fields)), rig.login)
    }

    @Test
    fun `a request shows progress and clears the previous error`() {
        rig.showLogin()
        rig.submitLogin("", "")

        rig.submitLogin("name", "secret")

        assertEquals(submitting, rig.login)
    }

    @Test
    fun `a failed login reports why and leaves the dialog open`() {
        rig.failLogins()
        rig.showLogin()

        rig.submitLogin("name", "secret")
        rig.deliver()

        assertEquals(open.copy(error = message(R.string.network_error)), rig.login)
    }

    @Test
    fun `reopening clears the error`() {
        rig.showLogin()
        rig.submitLogin("", "")

        rig.showLogin()

        assertEquals(open, rig.login)
    }

    @Test
    fun `dismissing cancels a running login`() {
        rig.failLogins()
        rig.showLogin()
        rig.submitLogin("name", "secret")

        rig.dismissLogin()
        rig.deliver()

        assertEquals(hidden, rig.login)
    }

    @Test
    fun `reopening during a login drops the progress state but not the login`() {
        rig.failLogins()
        rig.showLogin()
        rig.submitLogin("name", "secret")

        rig.showLogin()
        assertEquals(open, rig.login)
        rig.deliver()

        assertEquals(open.copy(error = message(R.string.network_error)), rig.login)
    }

    @Test
    fun `destroying the view cancels a running login but keeps the dialog`() {
        rig.failLogins()
        rig.showLogin()
        rig.submitLogin("name", "secret")

        rig.destroyView()
        rig.deliver()

        assertEquals(open, rig.login)
    }

    @Test
    fun `destroying the view keeps a shown error`() {
        rig.showLogin()
        rig.submitLogin("", "")

        rig.destroyView()

        assertEquals(open.copy(error = message(R.string.tournesol_login_missing_fields)), rig.login)
    }
}
