package no.nav.syfo.melding.kafka.config

import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.application.kafka.kafkaAivenProducerConfig
import no.nav.syfo.melding.kafka.domain.KafkaMeldingDTO
import org.apache.kafka.clients.producer.KafkaProducer

fun kafkaMeldingFraBehandlerProducerConfig(
    applicationEnvironmentKafka: KafkaEnvironment,
): KafkaProducer<String, KafkaMeldingDTO> {
    return KafkaProducer(
        kafkaAivenProducerConfig<KafkaMeldingDTOSerializer>(
            kafkaEnvironment = applicationEnvironmentKafka,
        )
    )
}
