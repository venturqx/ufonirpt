package org.schabi.newpipe.viewmodels

import androidx.lifecycle.SavedStateHandle
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.ui.components.video.comment.CommentInfo
import org.schabi.newpipe.util.KEY_URL
import org.schabi.newpipe.viewmodels.util.Resource

class CommentsViewModelTest {

    private fun createDummyCommentInfo(url: String = "https://www.youtube.com/watch?v=test") = CommentInfo(
        serviceId = 0,
        url = url,
        comments = emptyList(),
        nextPage = null,
        commentCount = 0,
        isCommentsDisabled = false
    )

    @Test
    fun `empty and blank URLs do not trigger commentsLoader and leave uiState in Loading`() = runBlocking {
        val requestedUrls = CopyOnWriteArrayList<String>()
        val handle = SavedStateHandle(mapOf(KEY_URL to ""))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CommentsViewModel(
            savedStateHandle = handle,
            commentsLoader = {
                requestedUrls.add(it)
                createDummyCommentInfo(it)
            },
            ioDispatcher = Dispatchers.Default,
            coroutineScope = scope
        )

        val emissions = CopyOnWriteArrayList<Resource<CommentInfo>>()
        val collectJob = scope.launch {
            viewModel.uiState.collect { emissions.add(it) }
        }

        // Allow any background dispatch for initial empty URL to manifest
        delay(50)
        assertEquals(Resource.Loading, viewModel.uiState.value)
        assertTrue("Loader must not be called for initial empty URL", requestedUrls.isEmpty())

        // Set blank whitespace URL and allow dispatch
        handle[KEY_URL] = "   "
        delay(50)
        assertTrue("Loader must not be called for blank whitespace URL", requestedUrls.isEmpty())
        assertEquals(Resource.Loading, viewModel.uiState.value)

        // Now set a valid URL to ensure it loads when URL is non-blank
        val validUrl = "https://www.youtube.com/watch?v=valid"
        handle[KEY_URL] = validUrl
        val successState = viewModel.uiState.first { it is Resource.Success }
        assertEquals(listOf(validUrl), requestedUrls)
        assertEquals(validUrl, (successState as Resource.Success).data.url)

        collectJob.cancel()
        scope.cancel()
    }

    @Test
    fun `default commentsLoader with empty URL does not trigger CommentsInfo getInfo and avoids Error trap`() = runBlocking {
        val handle = SavedStateHandle(mapOf(KEY_URL to ""))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CommentsViewModel(
            savedStateHandle = handle,
            ioDispatcher = Dispatchers.Default,
            coroutineScope = scope
        )

        val emissions = CopyOnWriteArrayList<Resource<CommentInfo>>()
        val collectJob = scope.launch {
            viewModel.uiState.collect { emissions.add(it) }
        }

        // Wait to allow any erroneous background extraction on Dispatchers.Default to execute
        delay(100)

        // Must remain in Resource.Loading, NOT Resource.Error
        assertEquals(listOf(Resource.Loading), emissions.toList())
        assertTrue("uiState should not be trapped in Resource.Error", viewModel.uiState.value !is Resource.Error)

        // Calling retry on empty URL must also not trigger CommentsInfo getInfo
        viewModel.retry()
        delay(100)
        assertEquals(listOf(Resource.Loading), emissions.toList())
        assertTrue("uiState should remain in Resource.Loading after retry on empty URL", viewModel.uiState.value is Resource.Loading)

        collectJob.cancel()
        scope.cancel()
    }

    @Test
    fun `calling retry while URL is empty does not trigger commentsLoader`() = runBlocking {
        val requestedUrls = CopyOnWriteArrayList<String>()
        val handle = SavedStateHandle()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = CommentsViewModel(
            savedStateHandle = handle,
            commentsLoader = {
                requestedUrls.add(it)
                createDummyCommentInfo(it)
            },
            ioDispatcher = Dispatchers.Default,
            coroutineScope = scope
        )

        val collectJob = scope.launch {
            viewModel.uiState.collect { }
        }

        viewModel.retry()
        delay(50)

        assertTrue("Loader must not be called when retry is invoked on empty URL", requestedUrls.isEmpty())
        assertEquals(Resource.Loading, viewModel.uiState.value)

        val validUrl = "https://www.youtube.com/watch?v=valid_after_empty_retry"
        handle[KEY_URL] = validUrl
        val successState = viewModel.uiState.first { it is Resource.Success }
        assertEquals(listOf(validUrl), requestedUrls)
        assertEquals(validUrl, (successState as Resource.Success).data.url)

        collectJob.cancel()
        scope.cancel()
    }

    @Test
    fun `retry recovers from Error to Loading and then Success`() = runBlocking {
        var attempts = 0
        val expectedException = IOException("Simulated network timeout")
        val dummyCommentInfo = createDummyCommentInfo()
        val handle = SavedStateHandle(mapOf(KEY_URL to "https://www.youtube.com/watch?v=retry_test"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val attempt2StartedLatch = CountDownLatch(1)
        val allowAttempt2Latch = CountDownLatch(1)

        val viewModel = CommentsViewModel(
            savedStateHandle = handle,
            commentsLoader = {
                attempts++
                if (attempts == 1) {
                    throw expectedException
                } else {
                    attempt2StartedLatch.countDown()
                    allowAttempt2Latch.await(5, TimeUnit.SECONDS)
                    dummyCommentInfo
                }
            },
            ioDispatcher = Dispatchers.Default,
            coroutineScope = scope
        )

        val collectJob = scope.launch {
            viewModel.uiState.collect { }
        }

        // First attempt fails
        val errorState = viewModel.uiState.first { it is Resource.Error }
        assertEquals(1, attempts)
        assertEquals(expectedException.message, (errorState as Resource.Error).throwable.message)

        // Trigger retry
        viewModel.retry()

        // Verify Loading is emitted while attempt 2 is actively executing
        assertTrue("Attempt 2 should start", attempt2StartedLatch.await(5, TimeUnit.SECONDS))
        val loadingState = viewModel.uiState.first { it is Resource.Loading }
        assertEquals(Resource.Loading, loadingState)

        // Allow attempt 2 to finish successfully
        allowAttempt2Latch.countDown()

        // Second attempt succeeds
        val successState = viewModel.uiState.first { it is Resource.Success }
        assertEquals(2, attempts)
        assertEquals(dummyCommentInfo, (successState as Resource.Success).data)

        collectJob.cancel()
        scope.cancel()
    }

    @Test
    fun `repeated failure on retry remains in Error with new throwable`() = runBlocking {
        var attempts = 0
        val firstException = IOException("Initial network failure")
        val secondException = IOException("Second network failure")
        val handle = SavedStateHandle(mapOf(KEY_URL to "https://www.youtube.com/watch?v=repeated_fail"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val attempt2StartedLatch = CountDownLatch(1)
        val allowAttempt2Latch = CountDownLatch(1)

        val viewModel = CommentsViewModel(
            savedStateHandle = handle,
            commentsLoader = {
                attempts++
                if (attempts == 1) {
                    throw firstException
                } else {
                    attempt2StartedLatch.countDown()
                    allowAttempt2Latch.await(5, TimeUnit.SECONDS)
                    throw secondException
                }
            },
            ioDispatcher = Dispatchers.Default,
            coroutineScope = scope
        )

        val collectJob = scope.launch {
            viewModel.uiState.collect { }
        }

        // First failure
        val firstErrorState = viewModel.uiState.first { it is Resource.Error }
        assertEquals(1, attempts)
        assertEquals(firstException.message, (firstErrorState as Resource.Error).throwable.message)

        // Trigger retry
        viewModel.retry()

        // Verify Loading is emitted while retry is actively in flight
        assertTrue("Attempt 2 should start", attempt2StartedLatch.await(5, TimeUnit.SECONDS))
        val loadingState = viewModel.uiState.first { it is Resource.Loading }
        assertEquals(Resource.Loading, loadingState)

        // Allow attempt 2 to complete with failure
        allowAttempt2Latch.countDown()

        // Second failure
        val secondErrorState = viewModel.uiState.first {
            it is Resource.Error && it.throwable.message == secondException.message
        }
        assertEquals(2, attempts)
        assertEquals(secondException.message, (secondErrorState as Resource.Error).throwable.message)

        collectJob.cancel()
        scope.cancel()
    }

    @Test
    fun `rapid retry clicks cancel in-flight request and recover cleanly without error`() = runBlocking {
        var attempts = 0
        val handle = SavedStateHandle(mapOf(KEY_URL to "https://www.youtube.com/watch?v=rapid_retry"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val firstAttemptStarted = CountDownLatch(1)
        val dummyCommentInfo = createDummyCommentInfo("https://www.youtube.com/watch?v=rapid_retry")

        val viewModel = CommentsViewModel(
            savedStateHandle = handle,
            commentsLoader = {
                attempts++
                if (attempts == 1) {
                    firstAttemptStarted.countDown()
                    Thread.sleep(200)
                }
                dummyCommentInfo
            },
            ioDispatcher = Dispatchers.IO,
            coroutineScope = scope
        )

        val emissions = CopyOnWriteArrayList<Resource<CommentInfo>>()
        val collectJob = scope.launch {
            viewModel.uiState.collect { emissions.add(it) }
        }

        // Wait for first attempt to begin execution
        assertTrue("First attempt should start", firstAttemptStarted.await(5, TimeUnit.SECONDS))

        // Rapidly invoke retry while first attempt is still running
        viewModel.retry()

        // Wait for second attempt to complete successfully
        val successState = viewModel.uiState.first { it is Resource.Success }
        assertEquals(dummyCommentInfo, (successState as Resource.Success).data)

        // Verify that CancellationException was not captured into Resource.Error
        val errorEmissions = emissions.filterIsInstance<Resource.Error>()
        assertTrue(
            "CancellationException must not be converted to Resource.Error",
            errorEmissions.none { it.throwable is CancellationException }
        )

        collectJob.cancel()
        scope.cancel()
    }

    @Test
    fun `navigating to new valid URL after error automatically recovers to Success without retry`() = runBlocking {
        var attempts = 0
        val handle = SavedStateHandle(mapOf(KEY_URL to "https://www.youtube.com/watch?v=failing_url"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val validUrl2 = "https://www.youtube.com/watch?v=recovered_url"

        val viewModel = CommentsViewModel(
            savedStateHandle = handle,
            commentsLoader = { url ->
                attempts++
                if (url == "https://www.youtube.com/watch?v=failing_url") {
                    throw IOException("Network error on video 1")
                } else {
                    createDummyCommentInfo(url)
                }
            },
            ioDispatcher = Dispatchers.Default,
            coroutineScope = scope
        )

        val emissions = CopyOnWriteArrayList<Resource<CommentInfo>>()
        val collectJob = scope.launch {
            viewModel.uiState.collect { emissions.add(it) }
        }

        // Wait for first attempt to fail
        val errorState = viewModel.uiState.first { it is Resource.Error }
        assertTrue((errorState as Resource.Error).throwable is IOException)

        // User navigates to video 2
        handle[KEY_URL] = validUrl2
        val successState = viewModel.uiState.first { it is Resource.Success }
        assertEquals(validUrl2, (successState as Resource.Success).data.url)

        collectJob.cancel()
        scope.cancel()
    }

    @Test
    fun `single argument constructor instantiates properly for Android SavedStateViewModelFactory`() {
        val handle = SavedStateHandle(mapOf(KEY_URL to ""))
        val viewModel = CommentsViewModel(handle)
        assertEquals(Resource.Loading, viewModel.uiState.value)
    }
}
