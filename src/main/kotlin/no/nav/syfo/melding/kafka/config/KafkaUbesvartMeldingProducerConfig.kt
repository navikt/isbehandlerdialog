package no.nav.syfo.melding.kafka.config

import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.application.kafka.kafkaAivenProducerConfig
import no.nav.syfo.melding.kafka.domain.KafkaUbesvartMeldingDTO
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.Serializer

fun kafkaUbesvartMeldingProducerConfig(
    applicationEnvironmentKafka: KafkaEnvironment,
): KafkaProducer<String, KafkaUbesvartMeldingDTO> {
    return KafkaProducer(
        kafkaAivenProducerConfig<KafkaUbesvartMeldingSerializer>(
            kafkaEnvironment = applicationEnvironmentKafka,
        )
    )
}

class KafkaUbesvartMeldingSerializer : Serializer<KafkaUbesvartMeldingDTO> {
    private val mapper = configuredJacksonMapper()
    override fun serialize(topic: String?, data: KafkaUbesvartMeldingDTO?): ByteArray =
        mapper.writeValueAsBytes(data)
}
