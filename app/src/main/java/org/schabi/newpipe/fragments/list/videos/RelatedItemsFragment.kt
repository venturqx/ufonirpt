package org.schabi.newpipe.fragments.list.videos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.material3.Surface
import androidx.fragment.compose.content
import com.evernote.android.state.State
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.ui.components.video.RelatedItems
import org.schabi.newpipe.ui.theme.AppTheme

class RelatedItemsFragment : BaseFragment() {
    @State
    @JvmField
    var streamInfo: StreamInfo? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = content {
        AppTheme {
            Surface {
                streamInfo?.let { RelatedItems(it) }
            }
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(info: StreamInfo) = RelatedItemsFragment().apply {
            streamInfo = info
        }
    }
}
