package no.nav.syfo.melding.kafka.config

import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.application.kafka.kafkaAivenProducerConfig
import no.nav.syfo.melding.kafka.domain.KafkaMeldingFraBehandlerDTO
import org.apache.kafka.clients.producer.KafkaProducer

fun kafkaMeldingFraBehandlerProducerConfig(
    applicationEnvironmentKafka: KafkaEnvironment,
): KafkaProducer<String, KafkaMeldingFraBehandlerDTO> {
    return KafkaProducer(
        kafkaAivenProducerConfig<KafkaMeldingFraBehandlerSerializer>(
            kafkaEnvironment = applicationEnvironmentKafka,
        )
    )
}
