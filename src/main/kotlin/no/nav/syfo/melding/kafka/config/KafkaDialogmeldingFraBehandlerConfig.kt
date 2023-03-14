package no.nav.syfo.melding.kafka.config

import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.application.kafka.kafkaConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import java.util.*

fun kafkaDialogmeldingFraBehandlerConsumerConfig(
    applicationEnvironmentKafka: KafkaEnvironment,
): Properties {
    return kafkaConsumerConfig(applicationEnvironmentKafka).apply {
        this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
        this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] =
            JacksonKafkaDeserializerDialogmeldingFraBehandler::class.java.canonicalName
    }
}
