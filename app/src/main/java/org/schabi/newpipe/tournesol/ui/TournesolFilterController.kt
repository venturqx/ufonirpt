package org.schabi.newpipe.tournesol.ui

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.chip.Chip
import org.schabi.newpipe.R
import org.schabi.newpipe.tournesol.TournesolDateRange
import org.schabi.newpipe.tournesol.TournesolFilterConfig
import org.schabi.newpipe.tournesol.TournesolFilterStore

private const val FILTER_SHEET_TAG = "TournesolFilters"

private fun quickChipId(dateRange: TournesolDateRange): Int = when (dateRange) {
    TournesolDateRange.DAY -> R.id.chip_day
    TournesolDateRange.WEEK -> R.id.chip_week
    TournesolDateRange.MONTH -> R.id.chip_month
    TournesolDateRange.THREE_MONTHS -> R.id.chip_3_months
    TournesolDateRange.YEAR -> R.id.chip_year
    TournesolDateRange.FOREVER -> View.NO_ID
}

class TournesolFilterController(
    private val fragment: Fragment,
    private val listener: Listener
) {
    fun interface Listener {
        fun onFiltersChanged(config: TournesolFilterConfig)
    }

    var currentConfig: TournesolFilterConfig = TournesolFilterConfig()
        private set

    private var tournesolHeaderView: View? = null
    private var advancedBadge: BadgeDrawable? = null

    private val quickDateChips = TournesolDateRange.entries
        .filter { it.isQuick }
        .associateWith(::quickChipId)

    fun init(rootView: View) {
        currentConfig = filterStore().load()
        setupTournesolHeader(rootView)
    }

    fun onResume() {
        fragment.parentFragmentManager.setFragmentResultListener(
            TournesolFilterFragment.REQUEST_KEY,
            fragment.viewLifecycleOwner
        ) { _, _ -> onSheetApplied() }
        val previous = currentConfig
        currentConfig = filterStore().load()
        if (previous != currentConfig) {
            updateQuickChipSelection()
            notifyFiltersChanged()
        }
    }

    fun onDestroyView() {
        val chip = tournesolHeaderView?.findViewById<Chip>(R.id.chip_advanced)
        val badge = advancedBadge
        if (chip != null && badge != null) {
            BadgeUtils.detachBadgeDrawable(badge, chip)
        }
        advancedBadge = null
        tournesolHeaderView = null
    }

    private fun setupTournesolHeader(rootView: View) {
        val headerContainer = rootView.findViewById<ViewGroup>(R.id.kiosk_header_container)
            ?: return

        if (tournesolHeaderView == null) {
            val contextThemeWrapper = ContextThemeWrapper(
                fragment.requireContext(),
                com.google.android.material.R.style.Theme_MaterialComponents_DayNight
            )

            tournesolHeaderView = LayoutInflater.from(contextThemeWrapper)
                .inflate(R.layout.tournesol_header, headerContainer, false)

            // Wire up quick date chips
            for ((dateRange, chipId) in quickDateChips) {
                tournesolHeaderView?.findViewById<Chip>(chipId)?.setOnClickListener {
                    onQuickDateSelected(dateRange)
                }
            }

            // Wire up Unsafe toggle
            tournesolHeaderView?.findViewById<Chip>(R.id.chip_unsafe)?.setOnClickListener {
                onUnsafeToggled()
            }

            // Wire up Advanced button (action, not a toggle) + dot badge
            tournesolHeaderView?.findViewById<Chip>(R.id.chip_advanced)?.let { advancedChip ->
                advancedChip.isCheckable = false
                advancedChip.setOnClickListener { openFilterSheet() }
                val badge = BadgeDrawable.create(advancedChip.context).apply {
                    isVisible = false
                    horizontalOffset = (advancedChip.resources.displayMetrics.density * 4).toInt()
                    verticalOffset = (advancedChip.resources.displayMetrics.density * 4).toInt()
                    backgroundColor = androidx.core.content.ContextCompat.getColor(
                        advancedChip.context,
                        R.color.tournesol_filter_accent
                    )
                    badgeTextColor = androidx.core.content.ContextCompat.getColor(
                        advancedChip.context,
                        R.color.tournesol_chip_text_selected
                    )
                }
                advancedBadge = badge
                advancedChip.post {
                    BadgeUtils.attachBadgeDrawable(badge, advancedChip)
                }
            }

            updateQuickChipSelection()
        }

        if (tournesolHeaderView?.parent == null) {
            headerContainer.addView(tournesolHeaderView)
        }
        headerContainer.visibility = View.VISIBLE
    }

    private fun notifyFiltersChanged() {
        listener.onFiltersChanged(currentConfig)
    }

    private fun onUnsafeToggled() {
        currentConfig = currentConfig.copy(
            includeLowScoreVideos = !currentConfig.includeLowScoreVideos
        )
        filterStore().save(currentConfig)
        updateQuickChipSelection()
        notifyFiltersChanged()
    }

    private fun onQuickDateSelected(dateRange: TournesolDateRange) {
        if (currentConfig.dateRange == dateRange) return
        currentConfig = currentConfig.copy(dateRange = dateRange)
        filterStore().save(currentConfig)
        updateQuickChipSelection()
        notifyFiltersChanged()
    }

    private fun updateQuickChipSelection() {
        val headerView = tournesolHeaderView ?: return

        for ((dateRange, chipId) in quickDateChips) {
            val chip = headerView.findViewById<Chip>(chipId) ?: continue
            chip.isChecked = dateRange == currentConfig.dateRange
        }

        // Sync Unsafe chip
        headerView.findViewById<Chip>(R.id.chip_unsafe)?.isChecked =
            currentConfig.includeLowScoreVideos

        // Highlight Advanced chip if a non-quick filter is active
        advancedBadge?.isVisible = currentConfig.hasActiveAdvancedFilters()
    }

    private fun openFilterSheet() {
        TournesolFilterFragment().show(fragment.parentFragmentManager, FILTER_SHEET_TAG)
    }

    private fun onSheetApplied() {
        currentConfig = filterStore().load()
        updateQuickChipSelection()
        notifyFiltersChanged()
    }

    private fun filterStore() = TournesolFilterStore(
        PreferenceManager.getDefaultSharedPreferences(fragment.requireContext())
    )
}
