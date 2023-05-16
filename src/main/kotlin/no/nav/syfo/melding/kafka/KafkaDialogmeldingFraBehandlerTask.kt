package no.nav.syfo.melding.kafka

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.*
import no.nav.syfo.client.padm2.Padm2Client
import no.nav.syfo.melding.kafka.config.JacksonKafkaDeserializerDialogmeldingFraBehandler
import org.apache.kafka.clients.consumer.ConsumerConfig

const val DIALOGMELDING_FROM_BEHANDLER_TOPIC = "teamsykefravr.dialogmelding"

fun launchKafkaTaskDialogmeldingFraBehandler(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    database: DatabaseInterface,
    padm2Client: Padm2Client,
) {
    val kafkaDialogmeldingFraBehandlerConsumer = KafkaDialogmeldingFraBehandlerConsumer(
        database = database,
        padm2Client = padm2Client,
    )
    val consumerProperties =
        kafkaConsumerConfig<JacksonKafkaDeserializerDialogmeldingFraBehandler>(kafkaEnvironment).apply {
            this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
        }
    launchKafkaTask(
        applicationState = applicationState,
        topic = DIALOGMELDING_FROM_BEHANDLER_TOPIC,
        consumerProperties = consumerProperties,
        kafkaConsumerService = kafkaDialogmeldingFraBehandlerConsumer,
    )
}
