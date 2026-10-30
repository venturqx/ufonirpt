package org.schabi.newpipe.views

import org.schabi.newpipe.R

internal data class TournesolCriterionStyle(
    val id: String,
    val labelRes: Int,
    val descriptionRes: Int,
    val iconRes: Int,
    val color: Int
) {
    companion object {
        val ALL: List<TournesolCriterionStyle> = listOf(
            TournesolCriterionStyle(
                id = "reliability",
                labelRes = R.string.tournesol_criteria_reliability,
                descriptionRes = R.string.compare_criteria_desc_reliability,
                iconRes = R.drawable.reliability,
                color = 0xFF4F77DD.toInt()
            ),
            TournesolCriterionStyle(
                id = "pedagogy",
                labelRes = R.string.tournesol_criteria_pedagogy,
                descriptionRes = R.string.compare_criteria_desc_pedagogy,
                iconRes = R.drawable.pedagogy,
                color = 0xFFC28BED.toInt()
            ),
            TournesolCriterionStyle(
                id = "importance",
                labelRes = R.string.tournesol_criteria_importance,
                descriptionRes = R.string.compare_criteria_desc_importance,
                iconRes = R.drawable.importance,
                color = 0xFFDC8A5D.toInt()
            ),
            TournesolCriterionStyle(
                id = "layman_friendly",
                labelRes = R.string.tournesol_criteria_layman_friendly,
                descriptionRes = R.string.compare_criteria_desc_layman_friendly,
                iconRes = R.drawable.layman_friendly,
                color = 0xFF4BB061.toInt()
            ),
            TournesolCriterionStyle(
                id = "entertaining_relaxing",
                labelRes = R.string.tournesol_criteria_entertaining_relaxing,
                descriptionRes = R.string.compare_criteria_desc_entertaining_relaxing,
                iconRes = R.drawable.entertaining_relaxing,
                color = 0xFFD8B36D.toInt()
            ),
            TournesolCriterionStyle(
                id = "engaging",
                labelRes = R.string.tournesol_criteria_engaging,
                descriptionRes = R.string.compare_criteria_desc_engaging,
                iconRes = R.drawable.engaging,
                color = 0xFFDFC642.toInt()
            ),
            TournesolCriterionStyle(
                id = "diversity_inclusion",
                labelRes = R.string.tournesol_criteria_diversity_inclusion,
                descriptionRes = R.string.compare_criteria_desc_diversity_inclusion,
                iconRes = R.drawable.diversity_inclusion,
                color = 0xFF76C6CB.toInt()
            ),
            TournesolCriterionStyle(
                id = "better_habits",
                labelRes = R.string.tournesol_criteria_better_habits,
                descriptionRes = R.string.compare_criteria_desc_better_habits,
                iconRes = R.drawable.better_habits,
                color = 0xFF9DD654.toInt()
            ),
            TournesolCriterionStyle(
                id = "backfire_risk",
                labelRes = R.string.tournesol_criteria_backfire_risk,
                descriptionRes = R.string.compare_criteria_desc_backfire_risk,
                iconRes = R.drawable.backfire_risk,
                color = 0xFFD37A80.toInt()
            )
        )

        private val BY_ID = ALL.associateBy { it.id }
        private val CATALOG_INDEX = ALL.mapIndexed { index, style -> style.id to index }.toMap()

        @JvmStatic
        fun find(id: String): TournesolCriterionStyle? = BY_ID[id]

        @JvmStatic
        fun <T> sortedByCatalogOrder(items: List<T>, idOf: (T) -> String): List<T> = items.sortedBy { CATALOG_INDEX[idOf(it)] ?: Int.MAX_VALUE }
    }
}
