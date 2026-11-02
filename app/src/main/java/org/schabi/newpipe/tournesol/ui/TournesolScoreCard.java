package org.schabi.newpipe.tournesol.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.schabi.newpipe.R;
import org.schabi.newpipe.tournesol.CriterionDistribution;
import org.schabi.newpipe.tournesol.CriterionScore;
import org.schabi.newpipe.tournesol.TournesolEntityApi;
import org.schabi.newpipe.tournesol.TournesolEntityParser;
import org.schabi.newpipe.tournesol.TournesolHelper;
import org.schabi.newpipe.tournesol.TournesolRating;
import org.schabi.newpipe.tournesol.TournesolScoreCache;
import org.schabi.newpipe.views.DistributionCurve;
import org.schabi.newpipe.views.DistributionCurveView;
import org.schabi.newpipe.views.ScoreCacheUrls;
import org.schabi.newpipe.views.ScoreCardLoad;
import org.schabi.newpipe.views.TitledDescription;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class TournesolScoreCard extends LinearLayout {

    public interface RatingListener {
        void onRatingLoaded(boolean hasInsufficientReason, long score);
    }

    private static final class DescriptionRow {
        private final LinearLayout root;
        private final ImageView icon;
        private final TextView text;
        private final LinearLayout miniChart;

        DescriptionRow(final LinearLayout root, final ImageView icon, final TextView text,
                       final LinearLayout miniChart) {
            this.root = root;
            this.icon = icon;
            this.text = text;
            this.miniChart = miniChart;
        }
    }

    private static final int CHART_HEIGHT_DP = 28;
    private static final int DESCRIPTION_SURFACE_ALPHA = 0x10;

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
    private LinearLayout tournesolStatusRow;
    private ProgressBar tournesolStatusProgress;
    private TextView tournesolStatusText;
    private TextView tournesolStatusRetry;

    @Nullable
    private TournesolVideoRef video;
    @Nullable
    private RatingListener ratingListener;

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
        tournesolStatusRow = findViewById(R.id.tournesol_status_row);
        tournesolStatusProgress = findViewById(R.id.tournesol_status_progress);
        tournesolStatusText = findViewById(R.id.tournesol_status_text);
        tournesolStatusRetry = findViewById(R.id.tournesol_status_retry);
        tournesolStatusRetry.setOnClickListener(v -> retry());
    }

    private void retry() {
        if (video != null && ratingListener != null) {
            bind(video, ratingListener);
        }
    }

    private void showStatus(@StringRes final int messageRes, final boolean loading,
                            final boolean retryable) {
        tournesolStatusText.setText(messageRes);
        tournesolStatusProgress.setVisibility(loading ? VISIBLE : GONE);
        tournesolStatusRetry.setVisibility(retryable ? VISIBLE : GONE);
        tournesolStatusRow.setVisibility(VISIBLE);
    }

    public void bind(@NonNull final TournesolVideoRef videoRef,
                     @NonNull final RatingListener listener) {
        this.video = videoRef;
        this.ratingListener = listener;

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
        if (video == null) {
            return;
        }
        showStatus(R.string.tournesol_detail_loading, true, false);
        disposables.add(
                TournesolEntityApi.fetchRating(video.getServiceId(), video.getVideoId())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnTerminate(() -> load.onFinished(ScoreCardLoad.Fetch.INFO))
                .subscribe(
                        this::displayTournesolInfo,
                        throwable -> showStatus(
                                R.string.tournesol_detail_load_error, false, true),
                        () -> showStatus(R.string.tournesol_detail_not_rated, false, false)
                )
        );
    }

    private void displayTournesolInfo(final TournesolRating rating) {
        if (video == null) {
            return;
        }
        final double tournesolScore = rating.getScore();
        final List<String> unsafeReasons = rating.getUnsafeReasons();

        final long roundedScore = Math.round(tournesolScore);
        cacheScore(video, roundedScore, unsafeReasons);

        final int nComparisons = rating.getComparisonCount();
        final int nContributors = rating.getContributorCount();

        tournesolScoreValue.setText(LollipopChartViewKt.formatScore(tournesolScore, 1));
        tournesolContributors.setText(getResources().getQuantityString(
                R.plurals.tournesol_detail_contributors, nContributors, nContributors));
        tournesolComparisons.setText(
                getContext().getString(R.string.tournesol_detail_comparisons, nComparisons));

        final boolean isInsufficient = TournesolHelper.hasInsufficientReason(unsafeReasons);
        if (isInsufficient) {
            tournesolLogo.setVisibility(GONE);
            tournesolUnsafeEmoji.setVisibility(VISIBLE);
        }

        if (ratingListener != null) {
            ratingListener.onRatingLoaded(isInsufficient, roundedScore);
        }

        displayCriteriaScores(rating.getCriterionScores());

        tournesolStatusRow.setVisibility(GONE);
        tournesolInfoSection.setVisibility(VISIBLE);
    }

    private static void cacheScore(final TournesolVideoRef videoRef, final long roundedScore,
                                   final List<String> unsafeReasons) {
        for (final String url : ScoreCacheUrls.of(videoRef.getOriginalUrl(), videoRef.getUrl())) {
            TournesolScoreCache.INSTANCE.put(url, roundedScore, unsafeReasons);
        }
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

    private int[] criterionColors(final List<CriterionScore> entries) {
        final int shade = ContextCompat.getColor(getContext(), R.color.tournesol_accent_shade);
        final int[] colors = new int[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            final TournesolCriterionStyle style =
                    TournesolCriterionStyle.find(entries.get(i).getCriteria());
            colors[i] = ColorUtils.compositeColors(
                    shade, style != null ? style.getColor() : Color.WHITE);
        }
        return colors;
    }

    private void createLollipopChart(final List<CriterionScore> entries) {
        final LinearLayout wrapper = createChartWrapper();
        final LollipopChartView lollipopView = createLollipopView(entries);
        final DescriptionRow descRow = createDescriptionRow();

        wrapper.addView(lollipopView);
        wrapper.addView(descRow.root);

        lollipopView.setOnSelectionChangedListener(
                index -> onCriterionSelected(entries, index, descRow));

        tournesolLollipopContainer.removeAllViews();
        tournesolLollipopContainer.addView(wrapper);
        tournesolLollipopContainer.setVisibility(VISIBLE);
        tournesolCriteriaSection.setVisibility(VISIBLE);
    }

    private LinearLayout createChartWrapper() {
        final LinearLayout wrapper = new LinearLayout(getContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
        wrapper.setClipChildren(false);
        wrapper.setClipToPadding(false);
        return wrapper;
    }

    private LollipopChartView createLollipopView(final List<CriterionScore> entries) {
        final List<Drawable> icons = loadRadarIcons(entries);
        final int[] colors = criterionColors(entries);
        final LollipopChartView lollipopView = new LollipopChartView(
                getContext(), entries, icons, colors, dpToPx(18));

        final int glowPadding = dpToPx(8);
        final LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lollipopView.setLayoutParams(chartParams);
        lollipopView.setPadding(glowPadding, glowPadding, glowPadding, glowPadding);
        return lollipopView;
    }

    private DescriptionRow createDescriptionRow() {
        final LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        root.setBackground(createDescriptionBackground());
        final LinearLayout.LayoutParams rootParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rootParams.topMargin = dpToPx(4);
        root.setLayoutParams(rootParams);
        root.setVisibility(GONE);

        final ImageView icon = createDescriptionIcon();
        final TextView text = createDescriptionText();
        final LinearLayout miniChart = createMiniChartContainer();

        root.addView(icon);
        root.addView(text);
        root.addView(miniChart);
        return new DescriptionRow(root, icon, text, miniChart);
    }

    private GradientDrawable createDescriptionBackground() {
        final GradientDrawable background = new GradientDrawable();
        background.setColor(ColorUtils.setAlphaComponent(
                ContextCompat.getColor(getContext(), R.color.tournesol_content),
                DESCRIPTION_SURFACE_ALPHA));
        background.setCornerRadius(dpToPx(12));
        return background;
    }

    private ImageView createDescriptionIcon() {
        final ImageView icon = new ImageView(getContext());
        final int iconSize = dpToPx(28);
        icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        return icon;
    }

    private TextView createDescriptionText() {
        final TextView text = new TextView(getContext());
        final LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(dpToPx(12));
        text.setLayoutParams(textParams);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        text.setLineSpacing(dpToPx(2), 1f);
        text.setTextColor(ContextCompat.getColor(getContext(), R.color.tournesol_content_muted));
        text.setMaxLines(4);
        return text;
    }

    private LinearLayout createMiniChartContainer() {
        final LinearLayout miniChart = new LinearLayout(getContext());
        final LinearLayout.LayoutParams miniParams = new LinearLayout.LayoutParams(
                dpToPx(72), LinearLayout.LayoutParams.WRAP_CONTENT);
        miniParams.setMarginStart(dpToPx(12));
        miniChart.setLayoutParams(miniParams);
        return miniChart;
    }

    private void onCriterionSelected(final List<CriterionScore> entries,
                                     @Nullable final Integer index,
                                     final DescriptionRow descRow) {
        if (index == null) {
            descRow.root.setVisibility(GONE);
            return;
        }
        final CriterionScore entry = entries.get(index);
        final TournesolCriterionStyle style =
                TournesolCriterionStyle.find(entry.getCriteria());
        if (style != null) {
            descRow.icon.setImageResource(style.getIconRes());
            descRow.text.setText(descriptionText(style));
            descRow.root.setVisibility(VISIBLE);
            showMiniChart(descRow.miniChart, entry.getCriteria());
        }
    }

    private SpannableString descriptionText(final TournesolCriterionStyle style) {
        final TitledDescription description = TitledDescription.of(
                getContext().getString(style.getLabelRes()),
                getContext().getString(style.getDescriptionRes()));
        final SpannableString spannable = new SpannableString(description.getText());
        spannable.setSpan(new StyleSpan(Typeface.BOLD),
                0, description.getTitleEnd(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    private void showMiniChart(final LinearLayout miniChart, final String criteria) {
        miniChart.removeAllViews();
        final CriterionDistribution data = criteriaDistributions.get(criteria);
        if (data != null) {
            final View chart = createDistributionChart(
                    data.getBins(), data.getDistribution());
            chart.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(CHART_HEIGHT_DP)));
            miniChart.addView(chart);
            miniChart.setVisibility(VISIBLE);
        } else {
            miniChart.setVisibility(GONE);
        }
    }

    private void fetchTournesolDistribution() {
        if (video == null) {
            return;
        }
        disposables.add(
                TournesolEntityApi.fetchDistributions(video.getServiceId(), video.getVideoId())
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
                getContext(), bins, distribution, dpToPx(3) / 2f);
        curveView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(CHART_HEIGHT_DP)));
        return curveView;
    }

    private int dpToPx(final float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }
}
