package org.schabi.newpipe.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.paging.CommentsSource
import org.schabi.newpipe.ui.components.video.comment.CommentInfo
import org.schabi.newpipe.util.KEY_URL
import org.schabi.newpipe.viewmodels.util.Resource

class CommentsViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val commentsLoader: (String) -> CommentInfo = { CommentInfo(CommentsInfo.getInfo(it)) },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {
    constructor(savedStateHandle: SavedStateHandle) : this(
        savedStateHandle,
        { CommentInfo(CommentsInfo.getInfo(it)) },
        Dispatchers.IO,
        null
    )

    private val scope = coroutineScope ?: viewModelScope
    private val retryTrigger = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun setUrl(url: String?) {
        if (!url.isNullOrBlank()) {
            savedStateHandle[KEY_URL] = url
        }
    }

    fun retry() {
        retryTrigger.tryEmit(Unit)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<Resource<CommentInfo>> = savedStateHandle.getStateFlow(KEY_URL, "")
        .filter { it.isNotBlank() }
        .distinctUntilChanged()
        .flatMapLatest { url ->
            retryTrigger
                .onStart { emit(Unit) }
                .transformLatest {
                    emit(Resource.Loading)
                    yield()
                    val resource = try {
                        val data = withContext(ioDispatcher) {
                            commentsLoader(url)
                        }
                        Resource.Success(data)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Resource.Error(e)
                    }
                    emit(resource)
                }
        }
        .stateIn(scope, SharingStarted.Lazily, Resource.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val comments = uiState
        .filterIsInstance<Resource.Success<CommentInfo>>()
        .flatMapLatest {
            Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
                CommentsSource(it.data)
            }.flow
        }
        .cachedIn(scope)
}
