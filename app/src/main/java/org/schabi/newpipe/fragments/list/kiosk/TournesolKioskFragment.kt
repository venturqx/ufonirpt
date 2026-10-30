package org.schabi.newpipe.fragments.list.kiosk

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import java.util.function.Supplier
import org.schabi.newpipe.databinding.PlaylistControlBinding
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.fragments.list.playlist.PlaylistControlViewHolder
import org.schabi.newpipe.player.playqueue.KioskPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.util.PlayButtonHelper
import org.schabi.newpipe.util.TournesolFilterConfig
import org.schabi.newpipe.util.TournesolHelper

class TournesolKioskFragment : KioskFragment(), PlaylistControlViewHolder {
    private var tournesolFilterController: TournesolFilterController? = null
    private var playlistControlBinding: PlaylistControlBinding? = null

    protected override fun getListHeaderSupplier(): Supplier<View> {
        playlistControlBinding = PlaylistControlBinding
            .inflate(requireActivity().layoutInflater, itemsList, false)
        // Reduce playlist control bar height for Tournesol to match filter bar proportions
        val compactHeight = (38 * resources.displayMetrics.density).toInt()
        playlistControlBinding!!.playlistCtrlPlayBgButton.layoutParams.height = compactHeight
        return Supplier { playlistControlBinding!!.root }
    }

    override fun initViews(rootView: View?, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        if (rootView == null) {
            return
        }
        if (tournesolFilterController == null) {
            tournesolFilterController = TournesolFilterController(this) { config ->
                applyTournesolFilters(config, reload = true)
            }
        }
        tournesolFilterController?.init(rootView)
        val controller = tournesolFilterController ?: return
        applyTournesolFilters(controller.currentConfig, reload = false)
    }

    override fun onResume() {
        super.onResume()
        tournesolFilterController?.onResume()
    }

    override fun onDestroyView() {
        tournesolFilterController?.onDestroyView()
        tournesolFilterController = null
        playlistControlBinding = null
        super.onDestroyView()
    }

    private fun applyTournesolFilters(config: TournesolFilterConfig, reload: Boolean) {
        url = TournesolHelper.buildTournesolUrl(config)
        if (!reload) {
            return
        }
        currentInfo = null
        currentNextPage = null
        currentWorker?.dispose()
        reloadContent()
    }

    override fun handleResult(result: KioskInfo) {
        super.handleResult(result)

        val binding = playlistControlBinding ?: return
        binding.root.visibility = if (infoListAdapter.itemCount > 1) {
            View.VISIBLE
        } else {
            View.GONE
        }

        val hostActivity = activity as? AppCompatActivity ?: return
        PlayButtonHelper.initPlaylistControlClickListener(hostActivity, binding, this)
    }

    override fun getPlayQueue(): PlayQueue {
        val streamItems = infoListAdapter.itemsList
            .filterIsInstance<StreamInfoItem>()
        return KioskPlayQueue(serviceId, url, currentNextPage, streamItems, 0)
    }

    companion object {
        @JvmStatic
        @Throws(ExtractionException::class)
        fun getInstance(serviceId: Int, kioskId: String): TournesolKioskFragment {
            val instance = TournesolKioskFragment()
            val service: StreamingService = NewPipe.getService(serviceId)
            val kioskLinkHandlerFactory: ListLinkHandlerFactory =
                service.kioskList.getListLinkHandlerFactoryByType(kioskId)
            instance.setInitialData(
                serviceId,
                kioskLinkHandlerFactory.fromId(kioskId).url,
                kioskId
            )
            instance.kioskId = kioskId
            return instance
        }
    }
}
