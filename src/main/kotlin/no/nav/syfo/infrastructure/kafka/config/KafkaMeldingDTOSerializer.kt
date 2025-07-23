package no.nav.syfo.infrastructure.kafka.config

import no.nav.syfo.infrastructure.kafka.domain.KafkaMeldingDTO
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer

class KafkaMeldingDTOSerializer : Serializer<KafkaMeldingDTO> {
    private val mapper = configuredJacksonMapper()
    override fun serialize(topic: String?, data: KafkaMeldingDTO?): ByteArray =
        mapper.writeValueAsBytes(data)
}
