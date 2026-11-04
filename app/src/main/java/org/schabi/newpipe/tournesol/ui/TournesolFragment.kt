package org.schabi.newpipe.tournesol.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.ktx.serializable

class TournesolFragment : Fragment() {
    private var compareSelected = false
    private var compareFragment: CompareFragment? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_tournesol, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        attachCompare()
    }

    fun onCompareTabSelected() {
        compareSelected = true
        compareFragment?.requestStart()
    }

    private fun attachCompare() {
        val video = arguments?.serializable<TournesolVideoRef>(KEY_VIDEO) ?: return
        val fragment = childFragmentManager.findFragmentByTag(TAG_COMPARE) as? CompareFragment
            ?: CompareFragment.getInstance(video, embedInDetail = true, externalScroll = true).also {
                childFragmentManager.beginTransaction()
                    .replace(R.id.compare_container, it, TAG_COMPARE)
                    .commitAllowingStateLoss()
            }
        compareFragment = fragment
        if (compareSelected) {
            fragment.requestStart()
        }
    }

    companion object {
        private const val TAG_COMPARE = "tournesol_compare"
        private const val KEY_VIDEO = "tournesol_video"

        fun getInstance(info: StreamInfo): TournesolFragment {
            return TournesolFragment().apply {
                arguments = bundleOf(KEY_VIDEO to TournesolVideoRef.from(info))
            }
        }
    }
}
