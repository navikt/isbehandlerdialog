package no.nav.syfo.dialogmelding.kafka

import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.application.kafka.kafkaConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import java.util.*

fun kafkaDialogmeldingFromBehandlerConsumerConfig(
    applicationEnvironmentKafka: KafkaEnvironment,
): Properties {
    return kafkaConsumerConfig(applicationEnvironmentKafka).apply {
        this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] =
            JacksonKafkaDeserializerDialogmeldingFromBehandler::class.java.canonicalName
    }
}
