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

class TournesolScoresFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_tournesol_scores, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val video = arguments?.serializable<TournesolVideoRef>(KEY_VIDEO) ?: return
        val scoreCard = view.findViewById<TournesolScoreCard>(R.id.tournesol_score_card)
        val detailFragment = parentFragment as? VideoDetailFragment
        scoreCard.bind(video) { hasInsufficientReason, score ->
            detailFragment?.updateTournesolDetailIcon(hasInsufficientReason, score)
        }
    }

    companion object {
        private const val KEY_VIDEO = "tournesol_video"

        fun getInstance(info: StreamInfo): TournesolScoresFragment {
            return TournesolScoresFragment().apply {
                arguments = bundleOf(KEY_VIDEO to TournesolVideoRef.from(info))
            }
        }
    }
}
