package org.schabi.newpipe.local.dialog

import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaylistCreationDialogTest {
    private val undelivered = mutableListOf<Throwable>()
    private val errors = mutableListOf<Throwable>()
    private var successes = 0

    @Before
    fun captureUndeliveredErrors() {
        RxJavaPlugins.setErrorHandler { undelivered += it }
    }

    @After
    fun resetErrorHandler() {
        RxJavaPlugins.setErrorHandler(null)
    }

    private fun subscribe(source: Maybe<List<Long>>) {
        PlaylistCreationDialog.subscribeDetached(
            source,
            Schedulers.trampoline(),
            { successes++ },
            { errors += it }
        )
    }

    @Test
    fun `failed write reaches error consumer and not the global handler`() {
        val failure = IOException("disk full")

        subscribe(Maybe.error(failure))

        assertEquals(1, errors.size)
        assertSame(failure, errors.single())
        assertEquals(0, successes)
        assertTrue(undelivered.isEmpty())
    }

    @Test
    fun `successful write calls success once`() {
        subscribe(Maybe.just(listOf(1L, 2L)))

        assertEquals(1, successes)
        assertTrue(errors.isEmpty())
        assertTrue(undelivered.isEmpty())
    }

    @Test
    fun `empty source calls neither path`() {
        subscribe(Maybe.empty())

        assertEquals(0, successes)
        assertTrue(errors.isEmpty())
        assertTrue(undelivered.isEmpty())
    }
}
