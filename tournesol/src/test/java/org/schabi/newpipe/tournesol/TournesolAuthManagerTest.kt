package org.schabi.newpipe.tournesol

import android.content.Context
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.SingleSubject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`

class TournesolAuthManagerTest {
    @Test
    fun `getValidAccessToken does no storage work until subscribed`() {
        val context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(context)

        TournesolAuthManager.getValidAccessToken(context)

        verify(context).applicationContext
        verifyNoMoreInteractions(context)
    }

    @Test
    fun `login does no storage work until subscribed`() {
        val context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(context)

        TournesolAuthManager.login(context, "user", "password")

        verify(context).applicationContext
        verifyNoMoreInteractions(context)
    }

    @Test
    fun `persist runs before success is emitted`() {
        val events = mutableListOf<String>()

        authenticateThenPersist(Single.just("token"), Schedulers.trampoline()) {
            events += "persisted $it"
            true
        }
            .doOnComplete { events += "completed" }
            .test()
            .assertComplete()

        assertEquals(listOf("persisted token", "completed"), events)
    }

    @Test
    fun `persist is not called when authentication fails`() {
        val failure = IOException("offline")
        var persisted = false

        authenticateThenPersist(Single.error<String>(failure), Schedulers.trampoline()) {
            persisted = true
            true
        }
            .test()
            .assertError(failure)

        assertFalse(persisted)
    }

    @Test
    fun `persist throwing surfaces as an error instead of success`() {
        val failure = IllegalStateException("keystore unavailable")

        authenticateThenPersist(Single.just("token"), Schedulers.trampoline()) { throw failure }
            .test()
            .assertNotComplete()
            .assertError(failure)
    }

    @Test
    fun `persist returning false surfaces as an error instead of success`() {
        authenticateThenPersist(Single.just("token"), Schedulers.trampoline()) { false }
            .test()
            .assertNotComplete()
            .assertError(IllegalStateException::class.java)
    }

    @Test
    fun `persist does not run on the subscriber thread`() {
        val persistThread = AtomicReference<Thread>()

        authenticateThenPersist(Single.just("token"), Schedulers.newThread()) {
            persistThread.set(Thread.currentThread())
            true
        }
            .test()
            .awaitDone(5, TimeUnit.SECONDS)
            .assertComplete()

        assertNotNull(persistThread.get())
        assertNotSame(Thread.currentThread(), persistThread.get())
    }

    @Test
    fun `disposing before authentication completes means persist does not run`() {
        val auth = SingleSubject.create<String>()
        var persisted = false
        val observer = authenticateThenPersist(auth, Schedulers.trampoline()) {
            persisted = true
            true
        }.test()
        assertTrue(auth.hasObservers())

        observer.dispose()
        auth.onSuccess("token")

        assertFalse(persisted)
    }

    @Test
    fun `orMissingToken passes through a token`() {
        Maybe.just("token").orMissingToken().test().assertResult("token")
    }

    @Test
    fun `orMissingToken maps empty to MissingTokenException`() {
        Maybe.empty<String>().orMissingToken().test()
            .assertNoValues()
            .assertError(MissingTokenException::class.java)
    }

    @Test
    fun `orMissingToken passes an upstream error through unchanged`() {
        val failure = IllegalStateException("boom")

        Maybe.error<String>(failure).orMissingToken().test()
            .assertNoValues()
            .assertError(failure)
    }
}
