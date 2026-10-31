package org.schabi.newpipe.views

internal data class TitledDescription(val text: String, val titleEnd: Int) {
    companion object {
        @JvmStatic
        fun of(title: String, description: String): TitledDescription = TitledDescription("$title\n$description", title.length)
    }
}
