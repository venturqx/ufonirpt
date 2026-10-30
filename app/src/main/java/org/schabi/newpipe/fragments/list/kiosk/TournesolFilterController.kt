package org.schabi.newpipe.fragments.list.kiosk

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
import org.schabi.newpipe.util.TournesolFilterConfig
import org.schabi.newpipe.util.TournesolFilterStore

private const val FILTER_SHEET_TAG = "TournesolFilters"

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

    private val quickDateChips = linkedMapOf(
        "day" to R.id.chip_day,
        "week" to R.id.chip_week,
        "month" to R.id.chip_month,
        "3_months" to R.id.chip_3_months,
        "year" to R.id.chip_year
    )

    fun init(rootView: View) {
        currentConfig = filterStore().load()
        setupTournesolHeader(rootView)
        val restoredSheet = fragment.parentFragmentManager.findFragmentByTag(FILTER_SHEET_TAG)
        (restoredSheet as? TournesolFilterFragment)?.setListener(::applySheetConfig)
    }

    fun onResume() {
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
            for ((dateKey, chipId) in quickDateChips) {
                tournesolHeaderView?.findViewById<Chip>(chipId)?.setOnClickListener {
                    onQuickDateSelected(dateKey)
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

    private fun onQuickDateSelected(dateKey: String) {
        if (currentConfig.dateKey == dateKey) return
        currentConfig = currentConfig.copy(dateKey = dateKey)
        filterStore().save(currentConfig)
        updateQuickChipSelection()
        notifyFiltersChanged()
    }

    private fun updateQuickChipSelection() {
        val headerView = tournesolHeaderView ?: return

        for ((dateKey, chipId) in quickDateChips) {
            val chip = headerView.findViewById<Chip>(chipId) ?: continue
            chip.isChecked = dateKey == currentConfig.dateKey
        }

        // Sync Unsafe chip
        headerView.findViewById<Chip>(R.id.chip_unsafe)?.isChecked =
            currentConfig.includeLowScoreVideos

        // Highlight Advanced chip if a non-quick filter is active
        advancedBadge?.isVisible = currentConfig.hasActiveAdvancedFilters(quickDateChips.keys)
    }

    private fun openFilterSheet() {
        val filterFragment = TournesolFilterFragment()
        filterFragment.setInitialData(currentConfig)
        filterFragment.setListener(::applySheetConfig)
        filterFragment.show(fragment.parentFragmentManager, FILTER_SHEET_TAG)
    }

    private fun applySheetConfig(newConfig: TournesolFilterConfig) {
        currentConfig = newConfig
        filterStore().save(currentConfig)
        updateQuickChipSelection()
        notifyFiltersChanged()
    }

    private fun filterStore() = TournesolFilterStore(
        PreferenceManager.getDefaultSharedPreferences(fragment.requireContext())
    )
}
