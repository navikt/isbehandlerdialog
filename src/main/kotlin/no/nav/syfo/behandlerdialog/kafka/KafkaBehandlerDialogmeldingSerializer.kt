package no.nav.syfo.behandlerdialog.kafka

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer

private val mapper = configuredJacksonMapper()

class KafkaBehandlerDialogmeldingSerializer : Serializer<DialogmeldingBestillingDTO> {
    override fun serialize(topic: String?, data: DialogmeldingBestillingDTO?): ByteArray =
        mapper.writeValueAsBytes(data)
}
