package no.nav.syfo.infrastructure.kafka.janitor

import no.nav.syfo.ApplicationState
import no.nav.syfo.application.JanitorService
import no.nav.syfo.infrastructure.kafka.config.KafkaEnvironment
import no.nav.syfo.infrastructure.kafka.config.kafkaConsumerConfig
import no.nav.syfo.infrastructure.kafka.config.launchKafkaTask
import org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG
import java.util.Properties

const val JANITOR_EVENT_TOPIC = "teamsykefravr.syfojanitor-event"

fun launchKafkaTaskJanitor(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    janitorService: JanitorService,
) {
    val consumerProperties = Properties().apply {
        putAll(kafkaConsumerConfig<JanitorEventDeserializer>(kafkaEnvironment))
        this[MAX_POLL_RECORDS_CONFIG] = "1"
    }

    launchKafkaTask(
        applicationState = applicationState,
        topic = JANITOR_EVENT_TOPIC,
        consumerProperties = consumerProperties,
        kafkaConsumerService = JanitorEventConsumerService(janitorService),
    )
}
