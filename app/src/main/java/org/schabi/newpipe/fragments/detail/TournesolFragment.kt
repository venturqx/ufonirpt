package org.schabi.newpipe.fragments.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.ktx.serializable
import org.schabi.newpipe.util.KEY_INFO
import org.schabi.newpipe.views.TournesolScoreCard

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
        val info = arguments?.serializable<StreamInfo>(KEY_INFO) ?: return
        val scoreCard = view.findViewById<TournesolScoreCard>(R.id.tournesol_score_card)
        val detailFragment = parentFragment as? VideoDetailFragment
        scoreCard.bind(info) { hasInsufficientReason, score ->
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

        val info = arguments?.serializable<StreamInfo>(KEY_INFO) ?: return
        val compareFragment = CompareFragment.getInstance(
            info,
            embedInDetail = true,
            externalScroll = true
        )
        childFragmentManager.beginTransaction()
            .replace(R.id.compare_container, compareFragment, TAG_COMPARE)
            .commitAllowingStateLoss()
    }

    companion object {
        private const val TAG_COMPARE = "tournesol_compare"

        fun getInstance(info: StreamInfo): TournesolFragment {
            return TournesolFragment().apply {
                arguments = bundleOf(KEY_INFO to info)
            }
        }
    }
}
