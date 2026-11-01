package org.schabi.newpipe.tournesol.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.ktx.serializable

class TournesolFragment : Fragment() {
    private var compareRequested = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_tournesol, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val video = arguments?.serializable<TournesolVideoRef>(KEY_VIDEO) ?: return
        val scoreCard = view.findViewById<TournesolScoreCard>(R.id.tournesol_score_card)
        val detailFragment = parentFragment as? VideoDetailFragment
        scoreCard.bind(video) { hasInsufficientReason, score ->
            detailFragment?.updateTournesolDetailIcon(hasInsufficientReason, score)
        }
        attachCompareIfRequested()
    }

    override fun onResume() {
        super.onResume()
        attachCompareIfRequested()
    }

    fun ensureCompareAttached() {
        compareRequested = true
        attachCompareIfRequested()
    }

    private fun attachCompareIfRequested() {
        if (!compareRequested || view == null || childFragmentManager.isStateSaved) {
            return
        }
        if (childFragmentManager.findFragmentByTag(TAG_COMPARE) != null) {
            return
        }

        val video = arguments?.serializable<TournesolVideoRef>(KEY_VIDEO) ?: return
        val compareFragment = CompareFragment.getInstance(
            video,
            embedInDetail = true,
            externalScroll = true
        )
        childFragmentManager.beginTransaction()
            .replace(R.id.compare_container, compareFragment, TAG_COMPARE)
            .commitAllowingStateLoss()
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
