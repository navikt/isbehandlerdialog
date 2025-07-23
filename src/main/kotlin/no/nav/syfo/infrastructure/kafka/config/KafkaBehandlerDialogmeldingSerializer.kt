package no.nav.syfo.infrastructure.kafka.config

import no.nav.syfo.infrastructure.kafka.domain.DialogmeldingBestillingDTO
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer

class KafkaBehandlerDialogmeldingSerializer : Serializer<DialogmeldingBestillingDTO> {
    private val mapper = configuredJacksonMapper()
    override fun serialize(topic: String?, data: DialogmeldingBestillingDTO?): ByteArray =
        mapper.writeValueAsBytes(data)
}
