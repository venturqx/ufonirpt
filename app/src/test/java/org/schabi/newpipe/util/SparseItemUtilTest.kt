package org.schabi.newpipe.util

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SparseItemUtilTest {

    private val pluginErrors = mutableListOf<Throwable>()
    private val saveErrors = mutableListOf<Throwable>()

    @Before
    fun captureRxPluginErrors() {
        RxJavaPlugins.setErrorHandler { pluginErrors += it }
    }

    @After
    fun resetRxPluginErrors() {
        RxJavaPlugins.setErrorHandler(null)
    }

    @Test
    fun failedSaveReachesOnErrorOnceAndNeverTheRxPluginHandler() {
        val failure = IllegalStateException("database write failed")

        SparseItemUtil.saveInBackground(Completable.error(failure), Schedulers.trampoline()) {
            saveErrors += it
        }

        assertEquals(1, saveErrors.size)
        assertSame(failure, saveErrors.single())
        assertTrue(pluginErrors.isEmpty())
    }

    @Test
    fun successfulSaveReportsNoError() {
        SparseItemUtil.saveInBackground(Completable.complete(), Schedulers.trampoline()) {
            saveErrors += it
        }

        assertTrue(saveErrors.isEmpty())
        assertTrue(pluginErrors.isEmpty())
    }
}
