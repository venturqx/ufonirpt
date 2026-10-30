package org.schabi.newpipe.util

import java.util.Locale

class LocaleKeyedCache<T : Any>(private val factory: (Locale) -> T) {
    private val entry = ThreadLocal<Pair<Locale, T>>()

    fun get(locale: Locale): T {
        val cached: Pair<Locale, T>? = entry.get()
        if (cached != null && cached.first == locale) {
            return cached.second
        }
        return factory(locale).also { entry.set(locale to it) }
    }
}
