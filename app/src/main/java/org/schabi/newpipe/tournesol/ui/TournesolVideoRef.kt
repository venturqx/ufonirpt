package org.schabi.newpipe.tournesol.ui

import java.io.Serializable
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamInfo

data class TournesolVideoRef(
    val videoId: String,
    val originalUrl: String?,
    val stream: StreamEntity
) : Serializable {
    val serviceId: Int get() = stream.serviceId
    val url: String get() = stream.url

    companion object {
        fun from(info: StreamInfo): TournesolVideoRef {
            return TournesolVideoRef(
                videoId = info.id,
                originalUrl = info.originalUrl,
                stream = StreamEntity(info)
            )
        }
    }
}
