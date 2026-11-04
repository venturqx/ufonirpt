package org.schabi.newpipe.util

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ocpsoft.prettytime.PrettyTime
import org.ocpsoft.prettytime.TimeFormat
import org.ocpsoft.prettytime.TimeUnit

class LocalizationTest {
    @Test(expected = NullPointerException::class)
    fun `relativeTime() must fail without initializing pretty time`() {
        Localization.relativeTime(OffsetDateTime.of(2021, 1, 6, 0, 0, 0, 0, ZoneOffset.UTC))
    }

    @Test
    fun `relativeTime() with a OffsetDateTime must work`() {
        val prettyTime = PrettyTime(LocalDate.of(2021, 1, 1), ZoneOffset.UTC)
        prettyTime.locale = Locale.ENGLISH
        Localization.initPrettyTime(prettyTime)

        val offset = OffsetDateTime.of(2021, 1, 6, 0, 0, 0, 0, ZoneOffset.UTC)
        val actual = Localization.relativeTime(offset)

        assertEquals("5 days from now", actual)
    }

    @Test
    fun `initPrettyTime() removes decades before publishing the instance`() {
        val date = OffsetDateTime.of(2041, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val previous = PrettyTime(LocalDate.of(2021, 1, 1), ZoneOffset.UTC)
        previous.locale = Locale.ENGLISH
        Localization.initPrettyTime(previous)

        var seenDuringConfiguration: String? = null
        val next = object : PrettyTime(LocalDate.of(2031, 1, 1), ZoneOffset.UTC) {
            override fun <UNIT : TimeUnit> removeUnit(unit: Class<UNIT>): TimeFormat? {
                seenDuringConfiguration = Localization.relativeTime(date)
                return super.removeUnit(unit)
            }
        }
        next.locale = Locale.ENGLISH
        Localization.initPrettyTime(next)

        assertEquals("20 years from now", seenDuringConfiguration)
        assertEquals("10 years from now", Localization.relativeTime(date))
    }

    @Test
    fun `getAppLocale() follows the default locale when no custom locale is set`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRENCH)
            assertEquals(Locale.FRENCH, Localization.getAppLocale())

            Locale.setDefault(Locale.GERMAN)
            assertEquals(Locale.GERMAN, Localization.getAppLocale())
        } finally {
            Locale.setDefault(original)
        }
    }
}
