package org.schabi.newpipe.util

import android.content.SharedPreferences
import android.util.Log
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito

class TournesolAuthStorageTest {
    private val legacy = FakeAuthPrefs()
    private val encrypted = FakeAuthPrefs()
    private var opens = 0
    private var wipes = 0
    private var openFailuresLeft = 0

    private lateinit var logMock: MockedStatic<Log>

    private fun newStorage() = TournesolAuthStorage(
        legacyPrefs = legacy,
        openEncryptedPrefs = { openEncrypted() },
        wipeStorage = { wipes++ }
    )

    private fun openEncrypted(): SharedPreferences {
        opens++
        if (openFailuresLeft > 0) {
            openFailuresLeft--
            throw IllegalStateException("unreadable")
        }
        return encrypted
    }

    @Before
    fun setUp() {
        logMock = Mockito.mockStatic(Log::class.java)
    }

    @After
    fun tearDown() {
        logMock.close()
    }

    @Test
    fun `migration commits the legacy value then removes it`() {
        legacy.values[KEY] = "legacy"

        newStorage().prefs()

        assertEquals("legacy", encrypted.values[KEY])
        assertEquals(1, encrypted.commits)
        assertEquals(0, encrypted.applies)
        assertFalse(KEY in legacy.values)
    }

    @Test
    fun `an existing encrypted value is kept and the legacy value is still removed`() {
        legacy.values[KEY] = "legacy"
        encrypted.values[KEY] = "current"

        newStorage().prefs()

        assertEquals("current", encrypted.values[KEY])
        assertEquals(0, encrypted.commits)
        assertFalse(KEY in legacy.values)
    }

    @Test
    fun `a failed commit keeps the legacy value and retries on the next launch`() {
        legacy.values[KEY] = "legacy"
        encrypted.commitSucceeds = false

        newStorage().prefs()

        assertFalse(KEY in encrypted.values)
        assertEquals("legacy", legacy.values[KEY])

        encrypted.commitSucceeds = true
        newStorage().prefs()

        assertEquals("legacy", encrypted.values[KEY])
        assertFalse(KEY in legacy.values)
    }

    @Test
    fun `a blank legacy value is removed without being copied`() {
        legacy.values[KEY] = "   "

        newStorage().prefs()

        assertFalse(KEY in encrypted.values)
        assertEquals(0, encrypted.commits)
        assertFalse(KEY in legacy.values)
    }

    @Test
    fun `a failed open without reset returns null and does not wipe`() {
        openFailuresLeft = 1

        assertNull(newStorage().prefs(resetIfUnreadable = false))

        assertEquals(1, opens)
        assertEquals(0, wipes)
    }

    @Test
    fun `a failed open with reset wipes and retries`() {
        openFailuresLeft = 1

        assertSame(encrypted, newStorage().prefs(resetIfUnreadable = true))

        assertEquals(2, opens)
        assertEquals(1, wipes)
    }

    @Test
    fun `a second failure after the wipe returns null`() {
        openFailuresLeft = 2

        assertNull(newStorage().prefs(resetIfUnreadable = true))

        assertEquals(2, opens)
        assertEquals(1, wipes)
    }

    @Test
    fun `a successful open is cached and not reopened`() {
        val storage = newStorage()

        val first = storage.prefs()
        val second = storage.prefs()

        assertSame(first, second)
        assertEquals(1, opens)
    }

    @Test
    fun `a failed open is not cached and a later call retries`() {
        val storage = newStorage()
        openFailuresLeft = 1

        assertNull(storage.prefs())
        assertSame(encrypted, storage.prefs())

        assertEquals(2, opens)
    }

    private companion object {
        const val KEY = "tournesol_auth_state"
    }
}

private class FakeAuthPrefs : SharedPreferences by Mockito.mock(SharedPreferences::class.java) {
    val values = mutableMapOf<String, Any>()
    var commits = 0
    var applies = 0
    var commitSucceeds = true

    override fun contains(key: String): Boolean = key in values

    override fun getString(key: String, defValue: String?): String? = values[key] as String? ?: defValue

    override fun edit(): SharedPreferences.Editor = FakeAuthEditor(this)

    fun commit(pending: Map<String, String?>): Boolean {
        commits++
        if (commitSucceeds) {
            store(pending)
        }
        return commitSucceeds
    }

    fun apply(pending: Map<String, String?>) {
        applies++
        store(pending)
    }

    private fun store(pending: Map<String, String?>) {
        pending.forEach { (key, value) ->
            if (value == null) {
                values.remove(key)
            } else {
                values[key] = value
            }
        }
    }
}

private class FakeAuthEditor(
    private val prefs: FakeAuthPrefs
) : SharedPreferences.Editor by Mockito.mock(SharedPreferences.Editor::class.java) {
    private val pending = mutableMapOf<String, String?>()

    private fun put(key: String, value: String?): SharedPreferences.Editor = also { pending[key] = value }

    override fun putString(key: String, value: String?) = put(key, value)

    override fun remove(key: String) = put(key, null)

    override fun commit() = prefs.commit(pending)

    override fun apply() = prefs.apply(pending)
}
