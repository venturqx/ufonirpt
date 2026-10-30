package org.schabi.newpipe.fragments.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList

class TournesolUidTest {

    @Test
    fun `parse round-trips every prefix`() {
        listOf("yt:abc", "sc:abc", "peertube:abc").forEach { raw ->
            val uid = TournesolUid.parse(raw)

            assertEquals(raw, uid?.value)
            assertEquals(raw, uid.toString())
        }
    }

    @Test
    fun `parse maps prefixes to NewPipe services`() {
        assertEquals(ServiceList.YouTube.serviceId, TournesolUid.parse("yt:123")?.serviceId)
        assertEquals(ServiceList.SoundCloud.serviceId, TournesolUid.parse("sc:123")?.serviceId)
        assertEquals(
            ServiceList.PeerTube.serviceId,
            TournesolUid.parse("peertube:123")?.serviceId
        )
    }

    @Test
    fun `parse keeps colons inside the id`() {
        assertEquals("peertube:host:abc", TournesolUid.parse("peertube:host:abc")?.value)
    }

    @Test
    fun `parse rejects unknown prefixes, missing prefixes and blank ids`() {
        assertNull(TournesolUid.parse("vimeo:abc"))
        assertNull(TournesolUid.parse("abc"))
        assertNull(TournesolUid.parse(""))
        assertNull(TournesolUid.parse(":abc"))
        assertNull(TournesolUid.parse(" yt:abc"))
        assertNull(TournesolUid.parse("yt:"))
        assertNull(TournesolUid.parse("sc:   "))
        assertNull(TournesolUid.parse("entity_a_3"))
    }

    @Test
    fun `of builds the wire string for a supported service`() {
        assertEquals("yt:abc", TournesolUid.of(ServiceList.YouTube.serviceId, "abc")?.value)
        assertEquals("sc:abc", TournesolUid.of(ServiceList.SoundCloud.serviceId, "abc")?.value)
        assertEquals("peertube:abc", TournesolUid.of(ServiceList.PeerTube.serviceId, "abc")?.value)
    }

    @Test
    fun `of rejects an unsupported service and a blank id`() {
        assertNull(TournesolUid.of(ServiceList.Bandcamp.serviceId, "abc"))
        assertNull(TournesolUid.of(ServiceList.YouTube.serviceId, "   "))
    }

    @Test
    fun `supports matches the service table`() {
        assertTrue(TournesolUid.supports(ServiceList.YouTube.serviceId))
        assertTrue(TournesolUid.supports(ServiceList.SoundCloud.serviceId))
        assertTrue(TournesolUid.supports(ServiceList.PeerTube.serviceId))
        assertFalse(TournesolUid.supports(ServiceList.MediaCCC.serviceId))
        assertFalse(TournesolUid.supports(ServiceList.Bandcamp.serviceId))
    }

    @Test
    fun `youTubeId is set only for YouTube and is trimmed`() {
        assertEquals("abc", TournesolUid.parse("yt:abc")?.youTubeId)
        assertEquals("abc", TournesolUid.parse("yt:  abc ")?.youTubeId)
        assertNull(TournesolUid.parse("sc:abc")?.youTubeId)
        assertNull(TournesolUid.parse("peertube:abc")?.youTubeId)
    }

    @Test
    fun `uids compare by value`() {
        assertEquals(TournesolUid.parse("yt:abc"), TournesolUid.of(ServiceList.YouTube.serviceId, "abc"))
    }
}
