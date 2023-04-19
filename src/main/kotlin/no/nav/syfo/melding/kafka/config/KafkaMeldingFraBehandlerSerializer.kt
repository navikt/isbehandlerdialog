package no.nav.syfo.melding.kafka.config

import no.nav.syfo.melding.kafka.domain.KafkaMeldingFraBehandlerDTO
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer

class KafkaMeldingFraBehandlerSerializer : Serializer<KafkaMeldingFraBehandlerDTO> {
    private val mapper = configuredJacksonMapper()
    override fun serialize(topic: String?, data: KafkaMeldingFraBehandlerDTO?): ByteArray =
        mapper.writeValueAsBytes(data)
}
