package org.schabi.newpipe.viewmodels

import android.util.Log
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.SavedStateHandle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.schabi.newpipe.fragments.detail.StateSavingTabAdapter
import org.schabi.newpipe.ui.components.video.comment.CommentInfo
import org.schabi.newpipe.util.KEY_URL
import org.schabi.newpipe.viewmodels.util.Resource

class CommentsLoadingTest {

    private lateinit var logMock: MockedStatic<Log>

    @Before
    fun setUp() {
        logMock = Mockito.mockStatic(Log::class.java)
        logMock.`when`<Boolean> { Log.isLoggable(anyString(), anyInt()) }.thenReturn(false)
    }

    @After
    fun tearDown() {
        logMock.close()
    }

    private fun createDummyCommentInfo(url: String) = CommentInfo(
        serviceId = 0,
        url = url,
        comments = emptyList(),
        nextPage = null,
        commentCount = 5,
        isCommentsDisabled = false
    )

    private fun createTestFragment(added: Boolean = false): Fragment {
        val fragment = spy(Fragment())
        doReturn(added).`when`(fragment).isAdded
        doNothing().`when`(fragment).setUserVisibleHint(anyBoolean())
        return fragment
    }

    @Test
    fun stateSavingTabAdapter_switchingVideo_doesNotPassFirstVideoSavedStateToSecondVideo() {
        val fm = mock(FragmentManager::class.java)
        val tx = mock(FragmentTransaction::class.java)
        val container = mock(ViewGroup::class.java)
        val video1SavedState = mock(Fragment.SavedState::class.java)

        `when`(container.id).thenReturn(1)
        `when`(fm.beginTransaction()).thenReturn(tx)
        `when`(tx.add(anyInt(), any(Fragment::class.java))).thenReturn(tx)
        `when`(tx.remove(any(Fragment::class.java))).thenReturn(tx)
        `when`(fm.saveFragmentInstanceState(any(Fragment::class.java))).thenReturn(video1SavedState)

        val adapter = StateSavingTabAdapter(fm)

        // Video 1: add and instantiate CommentsFragment at tab 0
        val fragmentVideo1 = createTestFragment(added = true)
        adapter.addFragment(fragmentVideo1, "COMMENTS")
        adapter.instantiateItem(container, 0)

        // Video 2: initTabs() clears items and adds new CommentsFragment at tab 0
        adapter.clearAllItems()
        val fragmentVideo2 = createTestFragment()
        adapter.addFragment(fragmentVideo2, "COMMENTS")

        // ViewPager sees POSITION_NONE for fragmentVideo1, destroys it, and instantiates fragmentVideo2
        adapter.destroyItem(container, 0, fragmentVideo1)
        adapter.instantiateItem(container, 0)

        // Expectation: fragmentVideo2 must NOT receive fragmentVideo1's saved state
        verify(fragmentVideo2, never()).setInitialSavedState(any())
    }

    @Test
    fun stateSavingTabAdapter_offscreenDestroyThenSwitchVideo_doesNotPassFirstVideoSavedStateToSecondVideo() {
        val fm = mock(FragmentManager::class.java)
        val tx = mock(FragmentTransaction::class.java)
        val container = mock(ViewGroup::class.java)
        val video1SavedState = mock(Fragment.SavedState::class.java)

        `when`(container.id).thenReturn(1)
        `when`(fm.beginTransaction()).thenReturn(tx)
        `when`(tx.add(anyInt(), any(Fragment::class.java))).thenReturn(tx)
        `when`(tx.remove(any(Fragment::class.java))).thenReturn(tx)
        `when`(fm.saveFragmentInstanceState(any(Fragment::class.java))).thenReturn(video1SavedState)

        val adapter = StateSavingTabAdapter(fm)

        // Video 1: CommentsFragment instantiated at tab 0, then destroyed when user swipes to tab 2
        val fragmentVideo1 = createTestFragment(added = true)
        adapter.addFragment(fragmentVideo1, "COMMENTS")
        adapter.instantiateItem(container, 0)
        adapter.destroyItem(container, 0, fragmentVideo1)

        // Video 2: initTabs() clears items and adds new CommentsFragment at tab 0
        adapter.clearAllItems()
        val fragmentVideo2 = createTestFragment()
        adapter.addFragment(fragmentVideo2, "COMMENTS")

        // User switches back to tab 0 on Video 2
        adapter.instantiateItem(container, 0)

        // Expectation: fragmentVideo2 must NOT receive fragmentVideo1's saved state
        verify(fragmentVideo2, never()).setInitialSavedState(any())
    }

    @Test
    fun stateSavingTabAdapter_updateItem_doesNotPassOldSavedStateToReplacementFragment() {
        val fm = mock(FragmentManager::class.java)
        val tx = mock(FragmentTransaction::class.java)
        val container = mock(ViewGroup::class.java)
        val oldSavedState = mock(Fragment.SavedState::class.java)

        `when`(container.id).thenReturn(1)
        `when`(fm.beginTransaction()).thenReturn(tx)
        `when`(tx.add(anyInt(), any(Fragment::class.java))).thenReturn(tx)
        `when`(tx.remove(any(Fragment::class.java))).thenReturn(tx)
        `when`(fm.saveFragmentInstanceState(any(Fragment::class.java))).thenReturn(oldSavedState)

        val adapter = StateSavingTabAdapter(fm)

        val fragment1 = createTestFragment(added = true)
        adapter.addFragment(fragment1, "COMMENTS")
        adapter.instantiateItem(container, 0)

        val fragment2 = createTestFragment()
        adapter.updateItem("COMMENTS", fragment2)

        adapter.destroyItem(container, 0, fragment1)
        adapter.instantiateItem(container, 0)

        verify(fragment2, never()).setInitialSavedState(any())
    }

    @Test
    fun stateSavingTabAdapter_sameFragmentSwipedOffscreenAndBack_restoresItsOwnSavedState() {
        val fm = mock(FragmentManager::class.java)
        val tx = mock(FragmentTransaction::class.java)
        val container = mock(ViewGroup::class.java)
        val ownSavedState = mock(Fragment.SavedState::class.java)

        `when`(container.id).thenReturn(1)
        `when`(fm.beginTransaction()).thenReturn(tx)
        `when`(tx.add(anyInt(), any(Fragment::class.java))).thenReturn(tx)
        `when`(tx.remove(any(Fragment::class.java))).thenReturn(tx)
        `when`(fm.saveFragmentInstanceState(any(Fragment::class.java))).thenReturn(ownSavedState)

        val adapter = StateSavingTabAdapter(fm)

        val fragment1 = createTestFragment(added = true)
        adapter.addFragment(fragment1, "COMMENTS")
        adapter.instantiateItem(container, 0)

        // Swiped offscreen
        adapter.destroyItem(container, 0, fragment1)
        doReturn(false).`when`(fragment1).isAdded

        // Swiped back to tab 0 on the same video
        adapter.instantiateItem(container, 0)

        verify(fragment1).setInitialSavedState(ownSavedState)
    }

    @Test
    fun commentsViewModel_resubscribingAfterStop_doesNotRefetchAlreadyLoadedComments() = runBlocking {
        val url1 = "https://www.youtube.com/watch?v=video1"
        val fetchCount = AtomicInteger(0)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val handle = SavedStateHandle(mapOf(KEY_URL to url1))
        val vm = CommentsViewModel(
            savedStateHandle = handle,
            commentsLoader = {
                fetchCount.incrementAndGet()
                createDummyCommentInfo(it)
            },
            ioDispatcher = Dispatchers.Default,
            coroutineScope = scope
        )

        // First subscription (e.g. initial tab view)
        val job1 = scope.launch { vm.uiState.collect {} }
        val firstResult = vm.uiState.first { it is Resource.Success }
        assertEquals(url1, (firstResult as Resource.Success).data.url)
        assertEquals(1, fetchCount.get())

        // Collector stops (e.g. user swipes to another tab or pauses)
        job1.cancel()
        delay(50)

        // Collector resubscribes on the same video
        val job2 = scope.launch { vm.uiState.collect {} }
        val secondResult = vm.uiState.first { it is Resource.Success }
        assertEquals(url1, (secondResult as Resource.Success).data.url)
        delay(50)

        // Expectation: commentsLoader was NOT called a second time for the same URL
        assertEquals(1, fetchCount.get())

        job2.cancel()
        scope.cancel()
    }

    @Test
    fun commentsViewModel_switchingUrlWhileFirstVideoSlowlyLoading_emitsSecondVideoWithoutWaitingForFirst() = runBlocking {
        val url1 = "https://www.youtube.com/watch?v=slow_video1"
        val url2 = "https://www.youtube.com/watch?v=fast_video2"
        val url1Started = CountDownLatch(1)
        val allowUrl1Finish = CountDownLatch(1)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val handle = SavedStateHandle(mapOf(KEY_URL to url1))
        val vm = CommentsViewModel(
            savedStateHandle = handle,
            commentsLoader = { url ->
                if (url == url1) {
                    url1Started.countDown()
                    allowUrl1Finish.await(2, TimeUnit.SECONDS)
                }
                createDummyCommentInfo(url)
            },
            ioDispatcher = Dispatchers.Default,
            coroutineScope = scope
        )

        val job = scope.launch { vm.uiState.collect {} }
        assertTrue(url1Started.await(1, TimeUnit.SECONDS))

        // Switch to url2 while url1 is still blocked loading
        handle[KEY_URL] = url2

        // Expectation: url2 loads promptly without waiting 2s for url1, and first Success is url2
        val success = vm.uiState.first { it is Resource.Success } as Resource.Success
        assertEquals(url2, success.data.url)

        allowUrl1Finish.countDown()
        job.cancel()
        scope.cancel()
    }

    @Test
    fun commentsViewModel_setUrl_overridesStaleRestoredSavedStateUrl() = runBlocking {
        val staleVideo1Url = "https://www.youtube.com/watch?v=video1"
        val currentVideo2Url = "https://www.youtube.com/watch?v=video2"

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val handle = SavedStateHandle(mapOf(KEY_URL to staleVideo1Url))
        val vm = CommentsViewModel(
            savedStateHandle = handle,
            commentsLoader = { createDummyCommentInfo(it) },
            ioDispatcher = Dispatchers.Default,
            coroutineScope = scope
        )

        vm.setUrl(currentVideo2Url)

        val job = scope.launch { vm.uiState.collect {} }
        val success = vm.uiState.first { it is Resource.Success } as Resource.Success
        assertEquals(currentVideo2Url, success.data.url)

        job.cancel()
        scope.cancel()
    }
}
