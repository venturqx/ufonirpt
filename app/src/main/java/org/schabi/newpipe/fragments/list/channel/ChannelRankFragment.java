package org.schabi.newpipe.fragments.list.channel;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.evernote.android.state.State;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlaylistControlBinding;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.fragments.list.playlist.PlaylistControlViewHolder;
import org.schabi.newpipe.player.playqueue.KioskPlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.tournesol.TournesolKioskQuery;
import org.schabi.newpipe.ui.emptystate.EmptyStateUtil;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.PlayButtonHelper;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.Single;

public class ChannelRankFragment extends BaseListInfoFragment<StreamInfoItem, KioskInfo>
        implements PlaylistControlViewHolder {

    @State
    protected String channelName;

    private PlaylistControlBinding playlistControlBinding;

    public ChannelRankFragment() {
        super(org.schabi.newpipe.error.UserAction.REQUESTED_KIOSK);
    }

    @NonNull
    public static ChannelRankFragment getInstance(final int serviceId,
                                                  @NonNull final String channelName) {
        final ChannelRankFragment instance = new ChannelRankFragment();
        instance.serviceId = serviceId;
        instance.channelName = channelName;
        instance.url = TournesolKioskQuery.forUploader(channelName).toUrl();
        return instance;
    }

    @Override
    protected Supplier<View> getListHeaderSupplier() {
        playlistControlBinding = PlaylistControlBinding
                .inflate(activity.getLayoutInflater(), itemsList, false);
        return playlistControlBinding::getRoot;
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_channel_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View rootView,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        EmptyStateUtil.setEmptyStateComposable(
                rootView.findViewById(R.id.empty_state_view));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        playlistControlBinding = null;
    }

    @Override
    public void setTitle(final String title) {
        if (!TextUtils.isEmpty(channelName)) {
            super.setTitle(channelName);
        } else {
            super.setTitle(title);
        }
    }

    @Override
    protected Single<KioskInfo> loadResult(final boolean forceLoad) {
        return ExtractorHelper.getKioskInfo(serviceId, url, forceLoad);
    }

    @Override
    protected Single<ListExtractor.InfoItemsPage<StreamInfoItem>> loadMoreItemsLogic() {
        return ExtractorHelper.getMoreKioskItems(serviceId, url, currentNextPage);
    }

    @Override
    public void handleResult(@NonNull final KioskInfo result) {
        super.handleResult(result);

        if (playlistControlBinding != null) {
            if (infoListAdapter.getItemCount() > 1) {
                playlistControlBinding.getRoot().setVisibility(View.VISIBLE);
            } else {
                playlistControlBinding.getRoot().setVisibility(View.GONE);
            }

            PlayButtonHelper.initPlaylistControlClickListener(
                    activity, playlistControlBinding, this);
        }
    }

    @Override
    public PlayQueue getPlayQueue() {
        final List<StreamInfoItem> streamItems = infoListAdapter.getItemsList()
                .stream()
                .filter(StreamInfoItem.class::isInstance)
                .map(StreamInfoItem.class::cast)
                .collect(Collectors.toList());

        return new KioskPlayQueue(serviceId, url, currentNextPage, streamItems, 0);
    }
}
