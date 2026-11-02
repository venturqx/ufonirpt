package org.schabi.newpipe.info_list.holder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.graphics.Typeface;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.tournesol.TournesolHelper;
import org.schabi.newpipe.tournesol.ui.TournesolCriterionStyle;
import org.schabi.newpipe.util.Localization;

import java.util.List;

/*
 * Created by Christian Schabesberger on 01.08.16.
 * <p>
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * StreamInfoItemHolder.java is part of NewPipe.
 * </p>
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * </p>
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * </p?
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe. If not, see <http://www.gnu.org/licenses/>.
 * </p>
 */

public class StreamInfoItemHolder extends StreamMiniInfoItemHolder {
    public final TextView itemAdditionalDetails;
    private final TextView itemInlineBestArrow;
    private final ImageView itemInlineBestIcon;
    private final TextView itemInlineWorstArrow;
    private final ImageView itemInlineWorstIcon;
    private final int tournesolScoreColor;
    @Nullable
    private Drawable tournesolLogo;

    public StreamInfoItemHolder(final InfoItemBuilder infoItemBuilder, final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_stream_item, parent);
    }

    public StreamInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                                final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);
        itemAdditionalDetails = itemView.findViewById(R.id.itemAdditionalDetails);
        itemInlineBestArrow = itemView.findViewById(R.id.itemInlineBestArrow);
        itemInlineBestIcon = itemView.findViewById(R.id.itemInlineBestIcon);
        itemInlineWorstArrow = itemView.findViewById(R.id.itemInlineWorstArrow);
        itemInlineWorstIcon = itemView.findViewById(R.id.itemInlineWorstIcon);
        tournesolScoreColor = ContextCompat.getColor(
                itemBuilder.getContext(), R.color.tournesol_score);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        super.updateFromItem(infoItem, historyRecordManager);

        if (!(infoItem instanceof StreamInfoItem)) {
            return;
        }
        final StreamInfoItem item = (StreamInfoItem) infoItem;

        final SpannableStringBuilder details = buildCompactDetailLine(item);

        if (details.length() > 0) {
            itemAdditionalDetails.setText(details);
            itemAdditionalDetails.setVisibility(View.VISIBLE);
        } else {
            itemAdditionalDetails.setVisibility(View.GONE);
        }

        bindInlineCriteria(item);
    }

    private SpannableStringBuilder buildCompactDetailLine(final StreamInfoItem item) {
        final int iconSize = (int) itemAdditionalDetails.getTextSize();
        final int textColor = itemAdditionalDetails.getCurrentTextColor();
        final SpannableStringBuilder sb = new SpannableStringBuilder();

        final List<DetailSegment> segments = DetailSegmentKt.detailSegments(
                item.getTournesolScore(),
                TournesolHelper.hasInsufficientReason(item.getTournesolUnsafeReasons()),
                Localization.compactRelativeTimeOrTextual(
                        item.getUploadDate(), item.getTextualUploadDate()),
                formatViewCount(item),
                item.getTournesolNComparisons());

        for (final DetailSegment segment : segments) {
            if (sb.length() > 0) {
                sb.append("  ");
            }
            if (segment instanceof DetailSegment.Score score) {
                appendScore(sb, score, iconSize);
            } else if (segment instanceof DetailSegment.Date date) {
                // Date: clock emoji + compact relative time (natural color)
                sb.append("\uD83D\uDD52\uFE0F\u2009");
                sb.append(date.getText());
            } else if (segment instanceof DetailSegment.Views views) {
                // Views: eye emoji + compact count (natural color)
                sb.append("\uD83D\uDC41\uFE0F\u2009");
                sb.append(views.getText());
            } else if (segment instanceof DetailSegment.Comparisons comparisons) {
                // Votes: balance scale emoji + count
                sb.append("\u2696\uFE0F\u2009");
                sb.append(String.valueOf(comparisons.getCount()));
            }
        }

        return sb;
    }

    @Nullable
    private String formatViewCount(final StreamInfoItem item) {
        if (item.getViewCount() < 0) {
            return null;
        }
        final Context context = itemBuilder.getContext();
        if (item.getStreamType().equals(StreamType.AUDIO_LIVE_STREAM)) {
            return Localization.listeningCount(context, item.getViewCount());
        } else if (item.getStreamType().equals(StreamType.LIVE_STREAM)) {
            return Localization.shortWatchingCount(context, item.getViewCount());
        } else {
            return Localization.shortCount(context, item.getViewCount());
        }
    }

    // Tournesol score: logo icon (or plant emoji if insufficient) + score (accent yellow)
    private void appendScore(final SpannableStringBuilder sb,
                             final DetailSegment.Score score,
                             final int iconSize) {
        if (score.getInsufficient()) {
            sb.append("\uD83C\uDF31\u2009");
        } else {
            appendLogo(sb, iconSize);
        }
        final int textStart = sb.length();
        sb.append(Long.toString(score.getValue()));
        sb.setSpan(new ForegroundColorSpan(tournesolScoreColor),
                textStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD),
                textStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new RelativeSizeSpan(1.15f),
                textStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void appendLogo(final SpannableStringBuilder sb, final int iconSize) {
        if (tournesolLogo == null) {
            tournesolLogo = ContextCompat.getDrawable(
                    itemBuilder.getContext(), R.drawable.logo_small);
        }
        if (tournesolLogo != null) {
            tournesolLogo.setBounds(0, 0, iconSize, iconSize);
            sb.append(" ");
            sb.setSpan(new CenteredImageSpan(tournesolLogo),
                    sb.length() - 1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append("\u2009");
        }
    }

    private static class CenteredImageSpan extends ImageSpan {
        CenteredImageSpan(final Drawable d) {
            super(d, ImageSpan.ALIGN_BASELINE);
        }

        @Override
        public void draw(final Canvas canvas, final CharSequence text,
                         final int start, final int end, final float x,
                         final int top, final int y, final int bottom,
                         final Paint paint) {
            final Drawable d = getDrawable();
            final Paint.FontMetricsInt fm = paint.getFontMetricsInt();
            final int textCenter = y + (fm.descent + fm.ascent) / 2;
            final int transY = textCenter - d.getBounds().height() / 2;
            canvas.save();
            canvas.translate(x, transY);
            d.draw(canvas);
            canvas.restore();
        }
    }

    private void bindInlineCriteria(final StreamInfoItem item) {
        // Inline best criteria (right of uploader line)
        bindCriterion(itemInlineBestArrow, itemInlineBestIcon,
                getCriteriaIcon(item.getTournesolBestCriteria()));

        // Inline worst criteria (right of additional details line)
        bindCriterion(itemInlineWorstArrow, itemInlineWorstIcon,
                getCriteriaIcon(item.getTournesolWorstCriteria()));
    }

    private static void bindCriterion(@Nullable final TextView arrow,
                                      @Nullable final ImageView icon,
                                      @Nullable @DrawableRes final Integer iconRes) {
        if (arrow == null || icon == null) {
            return;
        }
        if (iconRes != null) {
            arrow.setVisibility(View.VISIBLE);
            icon.setImageResource(iconRes);
            icon.setVisibility(View.VISIBLE);
        } else {
            arrow.setVisibility(View.GONE);
            icon.setVisibility(View.GONE);
        }
    }

    @Nullable
    @DrawableRes
    private static Integer getCriteriaIcon(@Nullable final String criteria) {
        if (criteria == null) {
            return null;
        }
        final TournesolCriterionStyle style = TournesolCriterionStyle.find(criteria);
        return style != null ? style.getIconRes() : null;
    }
}
