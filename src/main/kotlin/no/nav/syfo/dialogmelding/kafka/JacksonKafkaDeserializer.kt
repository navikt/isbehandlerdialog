package no.nav.syfo.dialogmelding.kafka

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Deserializer

val mapper = configuredJacksonMapper()

class JacksonKafkaDeserializerDialogmeldingFromBehandler : Deserializer<KafkaDialogmeldingFromBehandlerDTO> {
    override fun deserialize(topic: String, data: ByteArray): KafkaDialogmeldingFromBehandlerDTO =
        mapper.readValue(data, KafkaDialogmeldingFromBehandlerDTO::class.java)
    override fun close() {}
}
