package org.schabi.newpipe.fragments.detail

import org.schabi.newpipe.extractor.ServiceList

@ConsistentCopyVisibility
internal data class TournesolUid private constructor(
    private val service: Service,
    private val id: String
) {
    val value: String
        get() = "${service.prefix}:$id"

    val serviceId: Int
        get() = service.serviceId

    val youTubeId: String?
        get() = id.takeIf { service == Service.YOUTUBE }

    override fun toString(): String = value

    private enum class Service(val prefix: String, val serviceId: Int) {
        YOUTUBE("yt", ServiceList.YouTube.serviceId),
        SOUNDCLOUD("sc", ServiceList.SoundCloud.serviceId),
        PEERTUBE("peertube", ServiceList.PeerTube.serviceId)
    }

    companion object {
        fun parse(raw: String): TournesolUid? {
            val prefix = raw.substringBefore(':', missingDelimiterValue = "")
            val service = Service.entries.firstOrNull { it.prefix == prefix } ?: return null
            return create(service, raw.substringAfter(':'))
        }

        fun of(serviceId: Int, id: String): TournesolUid? {
            val service = serviceFor(serviceId) ?: return null
            return create(service, id)
        }

        fun supports(serviceId: Int): Boolean = serviceFor(serviceId) != null

        private fun serviceFor(serviceId: Int): Service? = Service.entries.firstOrNull { it.serviceId == serviceId }

        private fun create(service: Service, id: String): TournesolUid? = id.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { TournesolUid(service, it) }
    }
}
