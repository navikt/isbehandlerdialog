package no.nav.syfo.infrastructure.kafka.janitor

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer

class JanitorEventStatusSerializer : Serializer<JanitorEventStatusDTO> {
    private val mapper = configuredJacksonMapper()

    override fun serialize(topic: String?, data: JanitorEventStatusDTO?): ByteArray =
        mapper.writeValueAsBytes(data)
}
