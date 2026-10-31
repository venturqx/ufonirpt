package org.schabi.newpipe.views

import org.junit.Assert.assertEquals
import org.junit.Test

class TitledDescriptionTest {
    @Test
    fun `text is the title then a newline then the description`() {
        assertEquals("Title\nBody", TitledDescription.of("Title", "Body").text)
    }

    @Test
    fun `title end covers exactly the title`() {
        val titled = TitledDescription.of("Title", "Body")

        assertEquals("Title", titled.text.substring(0, titled.titleEnd))
    }

    @Test
    fun `title end counts utf16 units like a java string`() {
        val title = "🌱 Fiabilité"
        val titled = TitledDescription.of(title, "Body")

        assertEquals(title, titled.text.substring(0, titled.titleEnd))
    }
}
