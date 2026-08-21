package no.nav.syfo.infrastructure.kafka.janitor

import no.nav.syfo.infrastructure.kafka.config.KafkaEnvironment
import no.nav.syfo.infrastructure.kafka.config.kafkaAivenProducerConfig
import org.apache.kafka.clients.producer.KafkaProducer

fun janitorEventStatusProducerConfig(
    kafkaEnvironment: KafkaEnvironment,
): KafkaProducer<String, JanitorEventStatusDTO> =
    KafkaProducer(kafkaAivenProducerConfig<JanitorEventStatusSerializer>(kafkaEnvironment))
