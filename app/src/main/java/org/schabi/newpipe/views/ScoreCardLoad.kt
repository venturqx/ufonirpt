package org.schabi.newpipe.views

class ScoreCardLoad {
    enum class Fetch { INFO, DISTRIBUTION }

    private val unfinished = mutableSetOf<Fetch>()
    private var cancelled = false

    fun onBind(): Set<Fetch> {
        unfinished.addAll(Fetch.entries)
        cancelled = false
        return unfinished.toSet()
    }

    fun onFinished(fetch: Fetch) {
        unfinished.remove(fetch)
    }

    fun onDetached() {
        cancelled = true
    }

    fun onAttached(): Set<Fetch> {
        if (!cancelled) {
            return emptySet()
        }
        cancelled = false
        return unfinished.toSet()
    }
}
