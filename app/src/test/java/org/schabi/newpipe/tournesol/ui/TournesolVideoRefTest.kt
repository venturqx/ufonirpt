package org.schabi.newpipe.tournesol.ui

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

class TournesolVideoRefTest {

    @Test
    fun `from maps service id, id, url and originalUrl`() {
        val ref = TournesolVideoRef.from(streamInfo())

        assertEquals(SERVICE_ID, ref.serviceId)
        assertEquals(VIDEO_ID, ref.videoId)
        assertEquals(URL, ref.url)
        assertEquals(ORIGINAL_URL, ref.originalUrl)
    }

    @Test
    fun `from keeps a null originalUrl for the score cache to see`() {
        val info = streamInfo(originalUrl = null)

        assertNull(TournesolVideoRef.from(info).originalUrl)
    }

    @Test
    fun `from builds the same entity as a direct conversion`() {
        val info = streamInfo()

        val stream = TournesolVideoRef.from(info).stream

        assertEquals(StreamEntity(info), stream)
        assertEquals("uploader", stream.uploader)
        assertEquals("https://example.org/thumb.jpg", stream.thumbnailUrl)
    }

    @Test
    fun `from tolerates a null uploader name from an extractor`() {
        val info = streamInfo()
        StreamInfo::class.java.getMethod("setUploaderName", String::class.java).invoke(info, null)

        assertEquals("", TournesolVideoRef.from(info).stream.uploader)
    }

    @Test
    fun `survives a java serialization round trip`() {
        val ref = TournesolVideoRef.from(streamInfo())

        assertEquals(ref, deserialize(serialize(ref)))
    }

    @Test
    fun `serialized size does not grow with the heavy StreamInfo fields`() {
        val info = heavyStreamInfo()

        assertTrue(serialize(info).size > HEAVY_DESCRIPTION_CHARS)
        assertTrue(serialize(TournesolVideoRef.from(info)).size < MAX_SERIALIZED_BYTES)
    }

    private fun streamInfo(originalUrl: String? = ORIGINAL_URL): StreamInfo {
        val info = StreamInfo(
            SERVICE_ID,
            URL,
            originalUrl,
            StreamType.VIDEO_STREAM,
            VIDEO_ID,
            "title",
            0
        )
        info.uploaderName = "uploader"
        info.uploaderUrl = "https://example.org/channel"
        info.duration = 120
        info.viewCount = 9
        info.textualUploadDate = "2024-01-02"
        info.uploadDate = DateWrapper(OffsetDateTime.parse("2024-01-02T03:04:05Z"), true)
        info.thumbnails = listOf(
            Image("https://example.org/thumb.jpg", 360, 640, Image.ResolutionLevel.MEDIUM)
        )
        return info
    }

    private fun heavyStreamInfo(): StreamInfo {
        val info = streamInfo()
        info.description = Description.of(
            "d".repeat(HEAVY_DESCRIPTION_CHARS),
            Description.Type.PLAIN_TEXT
        )
        info.videoStreams = List(HEAVY_LIST_SIZE) { videoStream(it) }
        info.videoOnlyStreams = List(HEAVY_LIST_SIZE) { videoStream(it) }
        info.relatedItems = List(HEAVY_LIST_SIZE) {
            StreamInfoItem(SERVICE_ID, "$URL/$it", "related $it", StreamType.VIDEO_STREAM)
        }
        return info
    }

    private fun videoStream(index: Int): VideoStream {
        return VideoStream.Builder()
            .setId("stream$index")
            .setContent("https://example.org/stream/$index", true)
            .setMediaFormat(MediaFormat.MPEG_4)
            .setIsVideoOnly(false)
            .setResolution("720p")
            .build()
    }

    private fun serialize(value: Serializable): ByteArray {
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { it.writeObject(value) }
        return bytes.toByteArray()
    }

    private fun deserialize(bytes: ByteArray): Any {
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
    }

    private companion object {
        const val SERVICE_ID = 7
        const val VIDEO_ID = "abc123"
        const val URL = "https://example.org/watch?v=abc123"
        const val ORIGINAL_URL = "https://example.org/share/abc123?t=5"
        const val HEAVY_DESCRIPTION_CHARS = 1_000_000
        const val HEAVY_LIST_SIZE = 500
        const val MAX_SERIALIZED_BYTES = 4 * 1024
    }
}
