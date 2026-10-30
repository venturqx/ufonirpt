package org.schabi.newpipe.info_list.holder;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.ktx.ViewUtils;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.DependentPreferenceHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.StreamTypeUtil;
import org.schabi.newpipe.util.image.CoilHelper;
import org.schabi.newpipe.views.AnimatedProgressBar;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.SerialDisposable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.Consumer;

public class StreamMiniInfoItemHolder extends InfoItemHolder {
    private static final String TAG = StreamMiniInfoItemHolder.class.getSimpleName();

    public final ImageView itemThumbnailView;
    public final TextView itemVideoTitleView;
    public final TextView itemUploaderView;
    public final TextView itemDurationView;
    private final AnimatedProgressBar itemProgressView;
    private final SerialDisposable progressLoad = new SerialDisposable();

    StreamMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                             final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemVideoTitleView = itemView.findViewById(R.id.itemVideoTitleView);
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView);
        itemDurationView = itemView.findViewById(R.id.itemDurationView);
        itemProgressView = itemView.findViewById(R.id.itemProgressView);
    }

    public StreamMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_stream_mini_item, parent);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        progressLoad.set(null);
        if (!(infoItem instanceof StreamInfoItem)) {
            return;
        }
        final StreamInfoItem item = (StreamInfoItem) infoItem;

        itemVideoTitleView.setText(item.getName());
        itemUploaderView.setText(item.getUploaderName());

        if (item.getDuration() > 0) {
            itemDurationView.setText(Localization.getDurationString(item.getDuration()));
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.getContext(),
                    R.color.duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);

            itemProgressView.setVisibility(View.GONE);
            if (DependentPreferenceHelper
                    .getPositionsInListsEnabled(itemProgressView.getContext())) {
                loadProgress(infoItem, historyRecordManager,
                        state -> showProgress(item, state),
                        () -> itemProgressView.setVisibility(View.GONE));
            }
        } else if (StreamTypeUtil.isLiveStream(item.getStreamType())) {
            itemDurationView.setText(R.string.duration_live);
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.getContext(),
                    R.color.live_duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);
            itemProgressView.setVisibility(View.GONE);
        } else {
            itemDurationView.setVisibility(View.GONE);
            itemProgressView.setVisibility(View.GONE);
        }

        // Default thumbnail is shown on error, while loading and if the url is empty
        CoilHelper.INSTANCE.loadThumbnail(itemThumbnailView, item.getThumbnails());

        itemView.setOnClickListener(view -> {
            if (itemBuilder.getOnStreamSelectedListener() != null) {
                itemBuilder.getOnStreamSelectedListener().selected(item);
            }
        });

        switch (item.getStreamType()) {
            case AUDIO_STREAM:
            case VIDEO_STREAM:
            case LIVE_STREAM:
            case AUDIO_LIVE_STREAM:
            case POST_LIVE_STREAM:
            case POST_LIVE_AUDIO_STREAM:
                enableLongClick(item);
                break;
            case NONE:
            default:
                disableLongClick();
                break;
        }
    }

    @Override
    public void updateState(final InfoItem infoItem,
                            final HistoryRecordManager historyRecordManager) {
        progressLoad.set(null);
        final StreamInfoItem item = (StreamInfoItem) infoItem;

        if (DependentPreferenceHelper.getPositionsInListsEnabled(itemProgressView.getContext())) {
            loadProgress(infoItem, historyRecordManager,
                    state -> animateProgress(item, state),
                    this::animateProgressHidden);
        } else {
            animateProgressHidden();
        }
    }

    private void loadProgress(final InfoItem infoItem,
                              final HistoryRecordManager historyRecordManager,
                              final Consumer<StreamStateEntity> onState,
                              final Action onNoState) {
        progressLoad.set(historyRecordManager
                .loadStreamState(infoItem)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onState, throwable -> {
                    Log.e(TAG, "Failed to load stream state", throwable);
                    onNoState.run();
                }, onNoState));
    }

    private void showProgress(final StreamInfoItem item, final StreamStateEntity state) {
        itemProgressView.setVisibility(View.VISIBLE);
        itemProgressView.setMax((int) item.getDuration());
        itemProgressView.setProgress((int) TimeUnit.MILLISECONDS
                .toSeconds(state.getProgressMillis()));
    }

    private void animateProgress(final StreamInfoItem item, final StreamStateEntity state) {
        if (item.getDuration() > 0 && !StreamTypeUtil.isLiveStream(item.getStreamType())) {
            itemProgressView.setMax((int) item.getDuration());
            if (itemProgressView.getVisibility() == View.VISIBLE) {
                itemProgressView.setProgressAnimated((int) TimeUnit.MILLISECONDS
                        .toSeconds(state.getProgressMillis()));
            } else {
                itemProgressView.setProgress((int) TimeUnit.MILLISECONDS
                        .toSeconds(state.getProgressMillis()));
                ViewUtils.animate(itemProgressView, true, 500);
            }
        } else {
            animateProgressHidden();
        }
    }

    private void animateProgressHidden() {
        if (itemProgressView.getVisibility() == View.VISIBLE) {
            ViewUtils.animate(itemProgressView, false, 500);
        }
    }

    private void enableLongClick(final StreamInfoItem item) {
        itemView.setLongClickable(true);
        itemView.setOnLongClickListener(view -> {
            if (itemBuilder.getOnStreamSelectedListener() != null) {
                itemBuilder.getOnStreamSelectedListener().held(item);
            }
            return true;
        });
    }

    private void disableLongClick() {
        itemView.setLongClickable(false);
        itemView.setOnLongClickListener(null);
    }

}
