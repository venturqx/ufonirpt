package org.schabi.newpipe.fragments.detail.compare

internal data class ComparisonEntities(val entityA: String, val entityB: String)

internal fun entitiesForLeftRight(leftUid: String, rightUid: String): ComparisonEntities = ComparisonEntities(entityA = leftUid, entityB = rightUid)
