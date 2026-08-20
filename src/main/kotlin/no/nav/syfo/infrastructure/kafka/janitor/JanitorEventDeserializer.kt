package no.nav.syfo.infrastructure.kafka.janitor

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Deserializer

class JanitorEventDeserializer : Deserializer<JanitorEventDTO> {
    private val mapper = configuredJacksonMapper()

    override fun deserialize(topic: String, data: ByteArray): JanitorEventDTO =
        mapper.readValue(data, JanitorEventDTO::class.java)
}
