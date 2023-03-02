package no.nav.syfo.melding.kafka.config

import no.nav.syfo.melding.kafka.domain.KafkaDialogmeldingFraBehandlerDTO
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Deserializer

val mapper = configuredJacksonMapper()

class JacksonKafkaDeserializerDialogmeldingFraBehandler : Deserializer<KafkaDialogmeldingFraBehandlerDTO> {
    override fun deserialize(topic: String, data: ByteArray): KafkaDialogmeldingFraBehandlerDTO =
        mapper.readValue(data, KafkaDialogmeldingFraBehandlerDTO::class.java)
    override fun close() {}
}
