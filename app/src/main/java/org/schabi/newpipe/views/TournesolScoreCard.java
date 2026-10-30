package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class TournesolScoreCard extends LinearLayout {

    private static final int TOURNESOL_SCORE_COLOR = Color.parseColor("#FFCA1D");
    private static final int CHART_HEIGHT_DP = 28;

    private static final Map<String, Integer> CRITERIA_LABEL_MAP;
    private static final Map<String, Integer> CRITERIA_ICON_MAP;
    private static final Map<String, Integer> CRITERIA_DESCRIPTION_MAP;
    private static final Map<String, Integer> CRITERIA_COLOR_MAP;

    static {
        final Map<String, Integer> labels = new LinkedHashMap<>();
        labels.put("reliability", R.string.tournesol_criteria_reliability);
        labels.put("pedagogy", R.string.tournesol_criteria_pedagogy);
        labels.put("importance", R.string.tournesol_criteria_importance);
        labels.put("layman_friendly", R.string.tournesol_criteria_layman_friendly);
        labels.put("entertaining_relaxing", R.string.tournesol_criteria_entertaining_relaxing);
        labels.put("engaging", R.string.tournesol_criteria_engaging);
        labels.put("diversity_inclusion", R.string.tournesol_criteria_diversity_inclusion);
        labels.put("better_habits", R.string.tournesol_criteria_better_habits);
        labels.put("backfire_risk", R.string.tournesol_criteria_backfire_risk);
        CRITERIA_LABEL_MAP = Collections.unmodifiableMap(labels);

        final Map<String, Integer> icons = new LinkedHashMap<>();
        icons.put("reliability", R.drawable.reliability);
        icons.put("pedagogy", R.drawable.pedagogy);
        icons.put("importance", R.drawable.importance);
        icons.put("layman_friendly", R.drawable.layman_friendly);
        icons.put("entertaining_relaxing", R.drawable.entertaining_relaxing);
        icons.put("engaging", R.drawable.engaging);
        icons.put("diversity_inclusion", R.drawable.diversity_inclusion);
        icons.put("better_habits", R.drawable.better_habits);
        icons.put("backfire_risk", R.drawable.backfire_risk);
        CRITERIA_ICON_MAP = Collections.unmodifiableMap(icons);

        final Map<String, Integer> descs = new LinkedHashMap<>();
        descs.put("reliability", R.string.compare_criteria_desc_reliability);
        descs.put("pedagogy", R.string.compare_criteria_desc_pedagogy);
        descs.put("importance", R.string.compare_criteria_desc_importance);
        descs.put("layman_friendly", R.string.compare_criteria_desc_layman_friendly);
        descs.put("entertaining_relaxing", R.string.compare_criteria_desc_entertaining_relaxing);
        descs.put("engaging", R.string.compare_criteria_desc_engaging);
        descs.put("diversity_inclusion", R.string.compare_criteria_desc_diversity_inclusion);
        descs.put("better_habits", R.string.compare_criteria_desc_better_habits);
        descs.put("backfire_risk", R.string.compare_criteria_desc_backfire_risk);
        CRITERIA_DESCRIPTION_MAP = Collections.unmodifiableMap(descs);

        final Map<String, Integer> colors = new LinkedHashMap<>();
        colors.put("reliability", Color.parseColor("#4F77DD"));
        colors.put("pedagogy", Color.parseColor("#C28BED"));
        colors.put("importance", Color.parseColor("#DC8A5D"));
        colors.put("layman_friendly", Color.parseColor("#4BB061"));
        colors.put("entertaining_relaxing", Color.parseColor("#D8B36D"));
        colors.put("engaging", Color.parseColor("#DFC642"));
        colors.put("diversity_inclusion", Color.parseColor("#76C6CB"));
        colors.put("better_habits", Color.parseColor("#9DD654"));
        colors.put("backfire_risk", Color.parseColor("#D37A80"));
        CRITERIA_COLOR_MAP = Collections.unmodifiableMap(colors);
    }

    private final Map<String, CriterionDistribution> criteriaDistributions = new LinkedHashMap<>();
    private final CompositeDisposable disposables = new CompositeDisposable();

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
        fetchTournesolInfo();
        fetchTournesolDistribution();
    }

    @Override
    protected void onDetachedFromWindow() {
        disposables.clear();
        super.onDetachedFromWindow();
    }

    private void fetchTournesolInfo() {
        if (streamInfo == null) {
            return;
        }
        disposables.add(
                TournesolEntityApi.fetchRating(streamInfo.getServiceId(), streamInfo.getId())
                .observeOn(AndroidSchedulers.mainThread())
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
        final List<CriterionEntry> entries = new ArrayList<>();
        for (final CriterionScore item : criteriaScores) {
            final String criteria = item.getCriteria();
            final Integer labelRes = CRITERIA_LABEL_MAP.get(criteria);
            final String label = labelRes != null
                    ? getContext().getString(labelRes)
                    : criteria.replace("_", " ");
            entries.add(new CriterionEntry(criteria, label, item.getScore()));
        }

        final List<String> keyOrder = new ArrayList<>(CRITERIA_LABEL_MAP.keySet());
        entries.sort((a, b) -> {
            int ia = keyOrder.indexOf(a.id);
            int ib = keyOrder.indexOf(b.id);
            if (ia < 0) {
                ia = Integer.MAX_VALUE;
            }
            if (ib < 0) {
                ib = Integer.MAX_VALUE;
            }
            return Integer.compare(ia, ib);
        });

        if (!entries.isEmpty()) {
            createLollipopChart(entries);
        }
    }

    private List<Drawable> loadRadarIcons(final List<CriterionEntry> entries) {
        final List<Drawable> icons = new ArrayList<>();
        final int iconSizePx = dpToPx(32);
        for (final CriterionEntry entry : entries) {
            final Integer iconRes = CRITERIA_ICON_MAP.get(entry.id);
            if (iconRes != null) {
                final Drawable d = ContextCompat.getDrawable(getContext(), iconRes);
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

    private void createLollipopChart(final List<CriterionEntry> entries) {
        final List<Drawable> icons = loadRadarIcons(entries);
        final int[] colors = new int[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            final Integer c = CRITERIA_COLOR_MAP.get(entries.get(i).id);
            colors[i] = c != null ? c : Color.WHITE;
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

        lollipopView.setOnDimensionClickListener(index -> {
            final CriterionEntry entry = entries.get(index);
            final Integer iconRes = CRITERIA_ICON_MAP.get(entry.id);
            final Integer descRes = CRITERIA_DESCRIPTION_MAP.get(entry.id);
            if (iconRes != null && descRes != null) {
                descIcon.setImageResource(iconRes);
                final Integer labelRes = CRITERIA_LABEL_MAP.get(entry.id);
                final String title = labelRes != null
                        ? getContext().getString(labelRes) : entry.label;
                final String full = title + "\n" + getContext().getString(descRes);
                final SpannableString spannable = new SpannableString(full);
                spannable.setSpan(new StyleSpan(Typeface.BOLD),
                        0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                descText.setText(spannable);
                descRow.setVisibility(VISIBLE);

                miniChartContainer.removeAllViews();
                final CriterionDistribution data = criteriaDistributions.get(entry.id);
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
        final int chartHeightPx = dpToPx(CHART_HEIGHT_DP);
        final float strokePx = dpToPx(2) / 3f;

        final View curveView = new View(getContext()) {
            private final Paint curvePaint = buildCurvePaint();
            private final Paint fillPaint = buildFillPaint();
            private final Paint separatorPaint = buildSeparatorPaint();
            private final Path curvePath = new Path();
            private final Path fillPath = new Path();
            @Nullable
            private DistributionCurve curve;

            private Paint buildCurvePaint() {
                final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(TOURNESOL_SCORE_COLOR);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(strokePx);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                return p;
            }

            private Paint buildFillPaint() {
                final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(Color.argb(55, 0xFF, 0xCA, 0x1D));
                p.setStyle(Paint.Style.FILL);
                return p;
            }

            private Paint buildSeparatorPaint() {
                final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(Color.argb(140, 0xFF, 0xFF, 0xFF));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(strokePx * 0.6f);
                return p;
            }

            @Override
            protected void onSizeChanged(final int w, final int h,
                                         final int oldw, final int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                curve = DistributionCurve.of(bins, distribution, w, h);
                if (curve == null) {
                    return;
                }

                final float[] xs = curve.getXs();
                final float[] ys = curve.getYs();
                final float[] m = curve.getTangents();
                final int last = xs.length - 1;

                curvePath.reset();
                curvePath.moveTo(xs[0], ys[0]);
                for (int i = 0; i < last; i++) {
                    final float seg = (xs[i + 1] - xs[i]) / 3f;
                    curvePath.cubicTo(
                            xs[i] + seg, ys[i] + m[i] * seg,
                            xs[i + 1] - seg, ys[i + 1] - m[i + 1] * seg,
                            xs[i + 1], ys[i + 1]);
                }

                fillPath.set(curvePath);
                fillPath.lineTo(xs[last], h);
                fillPath.lineTo(xs[0], h);
                fillPath.close();
            }

            @Override
            protected void onDraw(final Canvas canvas) {
                if (curve == null) {
                    return;
                }
                canvas.drawPath(fillPath, fillPaint);
                canvas.drawPath(curvePath, curvePaint);
                final float zeroX = curve.getZeroX();
                canvas.drawLine(zeroX, 0f, zeroX, (float) getHeight(), separatorPaint);
            }
        };
        curveView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, chartHeightPx));
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

    private interface OnDimensionClickListener {
        void onDimensionClick(int index);
    }

    private static class CriterionEntry {
        final String id;
        final String label;
        final double score;

        CriterionEntry(final String id, final String label, final double score) {
            this.id = id;
            this.label = label;
            this.score = score;
        }
    }

    private static class LollipopChartView extends View {
        private final List<CriterionEntry> entries;
        private final List<Drawable> icons;
        private final int[] colors;
        private final int iconSizePx;
        private OnDimensionClickListener dimensionClickListener;
        private int selectedIndex = -1;

        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint circleStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint scorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint zeroLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint selectedGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final float[] circleCxArray;
        private final float[] circleCyArray;
        private float circleRadiusCached;

        LollipopChartView(final Context context,
                          final List<CriterionEntry> entries,
                          final List<Drawable> icons,
                          final int[] colors,
                          final int iconSizePx) {
            super(context);
            this.entries = entries;
            this.icons = icons;
            this.colors = colors;
            this.iconSizePx = iconSizePx;
            this.circleCxArray = new float[entries.size()];
            this.circleCyArray = new float[entries.size()];

            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setStrokeCap(Paint.Cap.BUTT);

            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(Color.parseColor("#0F0F0F"));

            circleStrokePaint.setStyle(Paint.Style.STROKE);
            circleStrokePaint.setStrokeWidth(dp(3.5f));

            scorePaint.setTextSize(dp(10f));
            scorePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            scorePaint.setTextAlign(Paint.Align.CENTER);
            scorePaint.setColor(Color.WHITE);

            zeroLinePaint.setStyle(Paint.Style.STROKE);
            zeroLinePaint.setColor(Color.parseColor("#40FFFFFF"));
            zeroLinePaint.setStrokeWidth(dp(1f));

            selectedGlowPaint.setStyle(Paint.Style.STROKE);
            selectedGlowPaint.setStrokeWidth(dp(4f));

            setClickable(true);
        }

        void setOnDimensionClickListener(final OnDimensionClickListener listener) {
            this.dimensionClickListener = listener;
        }

        @Override
        protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
            final int maxWidth = dp(400) + getPaddingLeft() + getPaddingRight();
            int clampedWidthSpec = widthMeasureSpec;
            if (MeasureSpec.getSize(widthMeasureSpec) > maxWidth) {
                clampedWidthSpec = MeasureSpec.makeMeasureSpec(
                        maxWidth, MeasureSpec.getMode(widthMeasureSpec));
            }
            super.onMeasure(clampedWidthSpec, heightMeasureSpec);

            double minS = 0;
            double maxS = 0;
            for (final CriterionEntry e : entries) {
                if (e.score < minS) {
                    minS = e.score;
                }
                if (e.score > maxS) {
                    maxS = e.score;
                }
            }
            final double range = maxS - minS;

            final float dpPerUnit = dp(2f);

            final double topMargin = maxS > 0 ? Math.max(3, range * 0.1) : 2;
            final double bottomMargin = minS < 0 ? Math.max(5, range * 0.2) : 2;
            final float barArea = (float) ((range + topMargin + bottomMargin) * dpPerUnit);

            final float circleRadius = dp(14f);
            final float scoreTextHeight = dp(14f);
            final float textGap = dp(3f);
            final float topOverhead = circleRadius + (maxS > 0 ? scoreTextHeight + textGap : 0);
            final float bottomOverhead = circleRadius
                    + (minS < 0 ? scoreTextHeight + textGap : 0);
            final float overhead = topOverhead + bottomOverhead;

            final int desiredHeight = (int) (overhead + barArea)
                    + getPaddingTop() + getPaddingBottom();
            final int maxHeight = dp(260f) + getPaddingTop() + getPaddingBottom();
            final int height = Math.min(desiredHeight, maxHeight);

            setMeasuredDimension(getMeasuredWidth(), height);
        }

        private int dp(final float dpVal) {
            return (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, dpVal,
                    getResources().getDisplayMetrics());
        }

        @Override
        public boolean onTouchEvent(final MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                final float tx = event.getX();
                final float ty = event.getY();
                final float touchRadius = circleRadiusCached + dp(8f);
                for (int i = 0; i < entries.size(); i++) {
                    final float dx = tx - circleCxArray[i];
                    final float dy = ty - circleCyArray[i];
                    if (dx * dx + dy * dy <= touchRadius * touchRadius) {
                        selectedIndex = (selectedIndex == i) ? -1 : i;
                        invalidate();
                        if (dimensionClickListener != null && selectedIndex >= 0) {
                            dimensionClickListener.onDimensionClick(selectedIndex);
                        }
                        return true;
                    }
                }
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(final Canvas canvas) {
            super.onDraw(canvas);
            final int n = entries.size();
            if (n == 0) {
                return;
            }

            final float w = getWidth();
            final float h = getHeight();
            final float circleRadius = dp(14f);
            circleRadiusCached = circleRadius;
            final float barWidth = dp(12f);

            final float scoreTextHeight = dp(14f);
            final float textGap = dp(3f);

            double minScore = 0;
            double maxScore = 0;
            for (final CriterionEntry entry : entries) {
                if (entry.score < minScore) {
                    minScore = entry.score;
                }
                if (entry.score > maxScore) {
                    maxScore = entry.score;
                }
            }

            final float topPadding = maxScore > 0 ? scoreTextHeight + textGap : 0;
            final float bottomPadding = minScore < 0 ? scoreTextHeight + textGap : 0;

            final float drawTop = topPadding + circleRadius;
            final float drawBottom = h - bottomPadding - circleRadius;

            final double range = maxScore - minScore;
            maxScore += maxScore > 0 ? Math.max(3, range * 0.1) : 2;
            minScore -= minScore < 0 ? Math.max(5, range * 0.2) : 2;
            final double totalRange = maxScore - minScore;

            final float zeroY = (float) (drawTop
                    + (maxScore / totalRange) * (drawBottom - drawTop));

            final float slotWidth = w / n;
            canvas.drawLine(slotWidth * 0.3f, zeroY,
                    w - slotWidth * 0.3f, zeroY, zeroLinePaint);

            for (int i = 0; i < n; i++) {
                final CriterionEntry entry = entries.get(i);
                final int color = colors[i];
                final float cx = slotWidth * (i + 0.5f);

                final float barEndY = (float) (drawTop
                        + ((maxScore - entry.score) / totalRange)
                        * (drawBottom - drawTop));

                circleCxArray[i] = cx;
                circleCyArray[i] = barEndY;

                barPaint.setColor(color);
                barPaint.setStrokeWidth(barWidth);
                canvas.drawLine(cx, zeroY, cx, barEndY, barPaint);

                if (i == selectedIndex) {
                    selectedGlowPaint.setColor(color);
                    selectedGlowPaint.setAlpha(160);
                    canvas.drawCircle(cx, barEndY, circleRadius + dp(3f),
                            selectedGlowPaint);
                }

                canvas.drawCircle(cx, barEndY, circleRadius, circlePaint);
                circleStrokePaint.setColor(color);
                canvas.drawCircle(cx, barEndY, circleRadius, circleStrokePaint);

                final Drawable icon = i < icons.size() ? icons.get(i) : null;
                if (icon != null) {
                    final int strokeInset = dp(3.5f) / 2;
                    int halfIconW = iconSizePx / 2 - strokeInset;
                    int halfIconH = iconSizePx / 2 - strokeInset;
                    float iconOffsetY = 0;
                    final String entryId = entry.id;
                    if ("layman_friendly".equals(entryId)) {
                        halfIconW = (int) (halfIconW * 0.88f);
                        halfIconH = (int) (halfIconH * 0.88f);
                    }
                    if ("backfire_risk".equals(entryId)) {
                        iconOffsetY = dp(1f);
                    }
                    final float iy = barEndY + iconOffsetY;
                    icon.setBounds(
                            (int) (cx - halfIconW), (int) (iy - halfIconH),
                            (int) (cx + halfIconW), (int) (iy + halfIconH));
                    icon.setTintList(null);
                    icon.draw(canvas);
                }

                final String scoreText = String.format(Locale.US, "%.0f", entry.score);
                scorePaint.setColor(color);
                if (entry.score >= 0) {
                    canvas.drawText(scoreText, cx,
                            barEndY - circleRadius - dp(6f), scorePaint);
                } else {
                    canvas.drawText(scoreText, cx,
                            barEndY + circleRadius + scoreTextHeight + dp(1f),
                            scorePaint);
                }
            }
        }
    }
}
