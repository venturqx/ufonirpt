package org.schabi.newpipe.fragments.detail.compare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.fragments.detail.CompareComparisonVideo
import org.schabi.newpipe.fragments.detail.ComparePairSelection

class CompareSelectionTest {

    private val youTubeId = ServiceList.YouTube.serviceId
    private val soundCloudId = ServiceList.SoundCloud.serviceId

    private fun video(uid: String, url: String? = "https://example.com/$uid", serviceId: Int? = null) = CompareComparisonVideo(
        uid = uid,
        title = "title",
        uploader = "uploader",
        thumbnailUrl = null,
        videoUrl = url,
        serviceId = serviceId
    )

    @Test
    fun `suggestionServiceId prefers the explicit service over the uid`() {
        assertEquals(youTubeId, suggestionServiceId(video("yt:a")))
        assertEquals(soundCloudId, suggestionServiceId(video("yt:a", serviceId = soundCloudId)))
        assertEquals(soundCloudId, suggestionServiceId(video("entity_a_3", serviceId = soundCloudId)))
        assertNull(suggestionServiceId(video("entity_a_3")))
    }

    @Test
    fun `suggestedPair selects two distinct resolvable videos`() {
        assertEquals(
            ComparePairSelection(youTubeId, "https://example.com/yt:a", soundCloudId, "https://example.com/sc:b"),
            suggestedPair(video("yt:a"), video("sc:b"))
        )
    }

    @Test
    fun `suggestedPair is null when either url is missing`() {
        assertNull(suggestedPair(video("yt:a", url = null), video("yt:b")))
        assertNull(suggestedPair(video("yt:a"), video("yt:b", url = null)))
    }

    @Test
    fun `suggestedPair is null when either service is unknown`() {
        assertNull(suggestedPair(video("entity_a_0"), video("yt:b")))
        assertNull(suggestedPair(video("yt:a"), video("entity_b_0")))
    }

    @Test
    fun `suggestedPair is null for the same video on both sides`() {
        assertNull(suggestedPair(video("yt:a"), video("yt:a")))
    }

    @Test
    fun `historyPair selects two different streams`() {
        assertEquals(
            ComparePairSelection(youTubeId, "a", youTubeId, "b"),
            historyPair(youTubeId, "a", youTubeId, "b")
        )
    }

    @Test
    fun `historyPair treats the same url on another service as a different stream`() {
        assertEquals(
            ComparePairSelection(youTubeId, "a", soundCloudId, "a"),
            historyPair(youTubeId, "a", soundCloudId, "a")
        )
    }

    @Test
    fun `historyPair is null for the same stream on both sides`() {
        assertNull(historyPair(youTubeId, "a", youTubeId, "a"))
    }
}
