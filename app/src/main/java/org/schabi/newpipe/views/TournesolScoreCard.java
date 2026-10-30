package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.util.CriterionDistribution;
import org.schabi.newpipe.util.CriterionScore;
import org.schabi.newpipe.util.TournesolEntityApi;
import org.schabi.newpipe.util.TournesolEntityParser;
import org.schabi.newpipe.util.TournesolHelper;
import org.schabi.newpipe.util.TournesolRating;
import org.schabi.newpipe.util.TournesolScoreCache;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class TournesolScoreCard extends LinearLayout {

    private static final int CHART_HEIGHT_DP = 28;

    private final Map<String, CriterionDistribution> criteriaDistributions = new LinkedHashMap<>();
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final ScoreCardLoad load = new ScoreCardLoad();

    private LinearLayout tournesolInfoSection;
    private LinearLayout tournesolCriteriaSection;
    private TextView tournesolScoreValue;
    private ImageView tournesolLogo;
    private TextView tournesolUnsafeEmoji;
    private LinearLayout tournesolDistributionContainer;
    private TextView tournesolContributors;
    private TextView tournesolComparisons;
    private FrameLayout tournesolLollipopContainer;

    @Nullable
    private StreamInfo streamInfo;
    @Nullable
    private VideoDetailFragment parentDetailFragment;

    public TournesolScoreCard(final Context context) {
        super(context);
        init();
    }

    public TournesolScoreCard(final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TournesolScoreCard(final Context context, @Nullable final AttributeSet attrs,
                              final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        LayoutInflater.from(getContext()).inflate(R.layout.view_tournesol_score_card, this, true);

        tournesolInfoSection = findViewById(R.id.tournesol_info_section);
        tournesolCriteriaSection = findViewById(R.id.tournesol_criteria_section);
        tournesolScoreValue = findViewById(R.id.tournesol_score_value);
        tournesolLogo = findViewById(R.id.tournesol_logo);
        tournesolUnsafeEmoji = findViewById(R.id.tournesol_unsafe_emoji);
        tournesolDistributionContainer = findViewById(R.id.tournesol_distribution_container);
        tournesolContributors = findViewById(R.id.tournesol_contributors);
        tournesolComparisons = findViewById(R.id.tournesol_comparisons);
        tournesolLollipopContainer = findViewById(R.id.tournesol_lollipop_container);
    }

    public void bind(@NonNull final StreamInfo info,
                     @Nullable final VideoDetailFragment parent) {
        this.streamInfo = info;
        this.parentDetailFragment = parent;

        tournesolInfoSection.setVisibility(GONE);
        tournesolCriteriaSection.setVisibility(GONE);
        tournesolLollipopContainer.removeAllViews();
        tournesolLollipopContainer.setVisibility(GONE);
        tournesolDistributionContainer.removeAllViews();
        tournesolDistributionContainer.setVisibility(GONE);
        tournesolLogo.setVisibility(VISIBLE);
        tournesolUnsafeEmoji.setVisibility(GONE);
        criteriaDistributions.clear();

        disposables.clear();
        startFetches(load.onBind());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startFetches(load.onAttached());
    }

    @Override
    protected void onDetachedFromWindow() {
        disposables.clear();
        load.onDetached();
        super.onDetachedFromWindow();
    }

    private void startFetches(final Set<ScoreCardLoad.Fetch> fetches) {
        if (fetches.contains(ScoreCardLoad.Fetch.INFO)) {
            fetchTournesolInfo();
        }
        if (fetches.contains(ScoreCardLoad.Fetch.DISTRIBUTION)) {
            fetchTournesolDistribution();
        }
    }

    private void fetchTournesolInfo() {
        if (streamInfo == null) {
            return;
        }
        disposables.add(
                TournesolEntityApi.fetchRating(streamInfo.getServiceId(), streamInfo.getId())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnTerminate(() -> load.onFinished(ScoreCardLoad.Fetch.INFO))
                .subscribe(
                        this::displayTournesolInfo,
                        throwable -> { /* silently ignore */ }
                )
        );
    }

    private void displayTournesolInfo(final TournesolRating rating) {
        if (streamInfo == null) {
            return;
        }
        final double tournesolScore = rating.getScore();
        final List<String> unsafeReasons = rating.getUnsafeReasons();

        final long roundedScore = Math.round(tournesolScore);
        final String originalUrl = streamInfo.getOriginalUrl();
        TournesolScoreCache.INSTANCE.put(originalUrl, roundedScore, unsafeReasons);
        if (!streamInfo.getUrl().equals(originalUrl)) {
            TournesolScoreCache.INSTANCE.put(streamInfo.getUrl(), roundedScore, unsafeReasons);
        }

        final int nComparisons = rating.getComparisonCount();
        final int nContributors = rating.getContributorCount();

        tournesolScoreValue.setText(String.format(Locale.US, "%.1f", tournesolScore));
        tournesolContributors.setText(
                getContext().getString(R.string.tournesol_detail_contributors, nContributors));
        tournesolComparisons.setText(
                getContext().getString(R.string.tournesol_detail_comparisons, nComparisons));

        final boolean isInsufficient = TournesolHelper.hasInsufficientReason(unsafeReasons);
        if (isInsufficient) {
            tournesolLogo.setVisibility(GONE);
            tournesolUnsafeEmoji.setVisibility(VISIBLE);
        }

        if (parentDetailFragment != null) {
            parentDetailFragment.updateTournesolDetailIcon(
                    isInsufficient, Math.round(tournesolScore));
        }

        displayCriteriaScores(rating.getCriterionScores());

        tournesolInfoSection.setVisibility(VISIBLE);
    }

    private void displayCriteriaScores(final List<CriterionScore> criteriaScores) {
        final List<CriterionScore> sortedEntries = TournesolCriterionStyle.sortedByCatalogOrder(
                criteriaScores, CriterionScore::getCriteria);

        if (!sortedEntries.isEmpty()) {
            createLollipopChart(sortedEntries);
        }
    }

    private List<Drawable> loadRadarIcons(final List<CriterionScore> entries) {
        final List<Drawable> icons = new ArrayList<>();
        final int iconSizePx = dpToPx(32);
        for (final CriterionScore entry : entries) {
            final TournesolCriterionStyle style =
                    TournesolCriterionStyle.find(entry.getCriteria());
            if (style != null) {
                final Drawable d = ContextCompat.getDrawable(getContext(), style.getIconRes());
                if (d != null) {
                    d.setBounds(0, 0, iconSizePx, iconSizePx);
                }
                icons.add(d);
            } else {
                icons.add(null);
            }
        }
        return icons;
    }

    private void createLollipopChart(final List<CriterionScore> entries) {
        final List<Drawable> icons = loadRadarIcons(entries);
        final int[] colors = new int[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            final TournesolCriterionStyle style =
                    TournesolCriterionStyle.find(entries.get(i).getCriteria());
            colors[i] = style != null ? style.getColor() : Color.WHITE;
        }

        final LinearLayout wrapper = new LinearLayout(getContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
        wrapper.setClipChildren(false);
        wrapper.setClipToPadding(false);

        final LollipopChartView lollipopView = new LollipopChartView(
                getContext(), entries, icons, colors, dpToPx(18));

        final int glowPadding = dpToPx(8);
        final LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lollipopView.setLayoutParams(chartParams);
        lollipopView.setPadding(glowPadding, glowPadding, glowPadding, glowPadding);

        final LinearLayout descRow = new LinearLayout(getContext());
        descRow.setOrientation(LinearLayout.HORIZONTAL);
        descRow.setGravity(Gravity.CENTER_VERTICAL);
        descRow.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), 0);
        descRow.setVisibility(GONE);

        final ImageView descIcon = new ImageView(getContext());
        final int iconSize = dpToPx(28);
        descIcon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));

        final TextView descText = new TextView(getContext());
        final LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(dpToPx(10));
        descText.setLayoutParams(textParams);
        descText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        descText.setTextColor(Color.parseColor("#B8FFFFFF"));
        descText.setMaxLines(4);

        final LinearLayout miniChartContainer = new LinearLayout(getContext());
        final LinearLayout.LayoutParams miniParams = new LinearLayout.LayoutParams(
                dpToPx(72), LinearLayout.LayoutParams.WRAP_CONTENT);
        miniParams.setMarginStart(dpToPx(8));
        miniChartContainer.setLayoutParams(miniParams);

        descRow.addView(descIcon);
        descRow.addView(descText);
        descRow.addView(miniChartContainer);

        wrapper.addView(lollipopView);
        wrapper.addView(descRow);

        lollipopView.setOnSelectionChangedListener(index -> {
            if (index == null) {
                descRow.setVisibility(GONE);
                return;
            }
            final CriterionScore entry = entries.get(index);
            final TournesolCriterionStyle style =
                    TournesolCriterionStyle.find(entry.getCriteria());
            if (style != null) {
                descIcon.setImageResource(style.getIconRes());
                final String title = getContext().getString(style.getLabelRes());
                final String full = title + "\n"
                        + getContext().getString(style.getDescriptionRes());
                final SpannableString spannable = new SpannableString(full);
                spannable.setSpan(new StyleSpan(Typeface.BOLD),
                        0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                descText.setText(spannable);
                descRow.setVisibility(VISIBLE);

                miniChartContainer.removeAllViews();
                final CriterionDistribution data =
                        criteriaDistributions.get(entry.getCriteria());
                if (data != null) {
                    final View chart = createDistributionChart(
                            data.getBins(), data.getDistribution());
                    chart.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(CHART_HEIGHT_DP)));
                    miniChartContainer.addView(chart);
                    miniChartContainer.setVisibility(VISIBLE);
                } else {
                    miniChartContainer.setVisibility(GONE);
                }
            }
        });

        tournesolLollipopContainer.removeAllViews();
        tournesolLollipopContainer.addView(wrapper);
        tournesolLollipopContainer.setVisibility(VISIBLE);
        tournesolCriteriaSection.setVisibility(VISIBLE);
    }

    private void fetchTournesolDistribution() {
        if (streamInfo == null) {
            return;
        }
        disposables.add(
                TournesolEntityApi.fetchDistributions(streamInfo.getServiceId(), streamInfo.getId())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnTerminate(() -> load.onFinished(ScoreCardLoad.Fetch.DISTRIBUTION))
                .subscribe(
                        this::displayDistribution,
                        throwable -> { /* silently ignore */ }
                )
        );
    }

    private void displayDistribution(final Map<String, CriterionDistribution> distributions) {
        criteriaDistributions.clear();
        criteriaDistributions.putAll(distributions);

        final CriterionDistribution lr =
                criteriaDistributions.get(TournesolEntityParser.LARGELY_RECOMMENDED);
        if (lr == null) {
            return;
        }
        tournesolDistributionContainer.removeAllViews();
        tournesolDistributionContainer.addView(
                createDistributionChart(lr.getBins(), lr.getDistribution()));
        tournesolDistributionContainer.setVisibility(VISIBLE);
    }

    private View createDistributionChart(final int[] bins, final int[] distribution) {
        if (!DistributionCurve.isDrawable(bins, distribution)) {
            return new View(getContext());
        }
        final View curveView = new DistributionCurveView(
                getContext(), bins, distribution, dpToPx(2) / 3f);
        curveView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(CHART_HEIGHT_DP)));
        return curveView;
    }

    private int dpToPx(final int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }

    private int dpToPx(final float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }
}
