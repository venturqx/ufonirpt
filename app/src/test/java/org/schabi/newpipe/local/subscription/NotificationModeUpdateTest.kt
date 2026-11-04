package org.schabi.newpipe.local.subscription

import io.reactivex.rxjava3.core.Completable
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.database.subscription.NotificationMode

class NotificationModeUpdateTest {
    @Test
    fun enabledModePersistsThenRemembersStreams() {
        val events = mutableListOf<String>()

        notificationModeUpdate(
            NotificationMode.ENABLED,
            Completable.fromAction { events.add("persist") }
        ) {
            Completable.fromAction { events.add("remember") }
        }.test().assertComplete()

        assertEquals(listOf("persist", "remember"), events)
    }

    @Test
    fun enabledModeDoesNotStartWorkBeforeSubscription() {
        val events = mutableListOf<String>()

        notificationModeUpdate(
            NotificationMode.ENABLED,
            Completable.fromAction { events.add("persist") }
        ) {
            events.add("rememberRequested")
            Completable.complete()
        }

        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun disabledModePersistsAndNeverRemembersStreams() {
        val events = mutableListOf<String>()

        notificationModeUpdate(
            NotificationMode.DISABLED,
            Completable.fromAction { events.add("persist") }
        ) {
            events.add("rememberRequested")
            Completable.complete()
        }.test().assertComplete()

        assertEquals(listOf("persist"), events)
    }

    @Test
    fun failedPersistPropagatesErrorAndNeverRemembersStreams() {
        val error = IllegalStateException("database write failed")
        var rememberRequests = 0

        notificationModeUpdate(NotificationMode.ENABLED, Completable.error(error)) {
            rememberRequests++
            Completable.complete()
        }.test().assertError(error)

        assertEquals(0, rememberRequests)
    }
}
