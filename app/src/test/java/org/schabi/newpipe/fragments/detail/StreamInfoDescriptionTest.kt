package org.schabi.newpipe.fragments.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.util.NO_SERVICE_ID

class StreamInfoDescriptionTest {
    private fun streamInfo() = StreamInfo(NO_SERVICE_ID, "", "", StreamType.VIDEO_STREAM, "", "", 0)

    @Test
    fun `description is null when state is absent`() {
        assertNull(descriptionOf(null))
    }

    @Test
    fun `description is null when stream has none`() {
        assertNull(descriptionOf(streamInfo()))
    }

    @Test
    fun `description comes from the stream`() {
        val description = Description("content", Description.Type.PLAIN_TEXT)
        val info = streamInfo().apply { setDescription(description) }

        assertSame(description, descriptionOf(info))
    }

    @Test
    fun `tags are empty when state is absent`() {
        assertTrue(tagsOf(null).isEmpty())
    }

    @Test
    fun `tags are empty when stream has none`() {
        assertTrue(tagsOf(streamInfo()).isEmpty())
    }

    @Test
    fun `tags come from the stream`() {
        val info = streamInfo().apply { setTags(listOf("one", "two")) }

        assertEquals(listOf("one", "two"), tagsOf(info))
    }
}
