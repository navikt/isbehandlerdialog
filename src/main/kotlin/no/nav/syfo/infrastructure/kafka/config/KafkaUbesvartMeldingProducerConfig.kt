package no.nav.syfo.infrastructure.kafka.config

import no.nav.syfo.infrastructure.kafka.domain.KafkaMeldingDTO
import org.apache.kafka.clients.producer.KafkaProducer

fun kafkaUbesvartMeldingProducerConfig(
    applicationEnvironmentKafka: KafkaEnvironment,
): KafkaProducer<String, KafkaMeldingDTO> {
    return KafkaProducer(
        kafkaAivenProducerConfig<KafkaMeldingDTOSerializer>(
            kafkaEnvironment = applicationEnvironmentKafka,
        )
    )
}
