package org.schabi.newpipe.views

internal fun selectionAfterTap(selected: Int?, hit: Int?): Int? = when (hit) {
    null -> selected
    selected -> null
    else -> hit
}
