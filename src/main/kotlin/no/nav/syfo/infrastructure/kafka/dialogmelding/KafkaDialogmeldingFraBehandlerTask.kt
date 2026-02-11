package no.nav.syfo.infrastructure.kafka.dialogmelding

import no.nav.syfo.ApplicationState
import no.nav.syfo.application.MeldingService
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.kafka.config.JacksonKafkaDeserializerDialogmeldingFraBehandler
import no.nav.syfo.infrastructure.kafka.config.KafkaEnvironment
import no.nav.syfo.infrastructure.kafka.config.kafkaConsumerConfig
import no.nav.syfo.infrastructure.kafka.config.launchKafkaTask
import org.apache.kafka.clients.consumer.ConsumerConfig

const val DIALOGMELDING_FROM_BEHANDLER_TOPIC = "teamsykefravr.dialogmelding"

fun launchKafkaTaskDialogmeldingFraBehandler(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    database: DatabaseInterface,
    meldingService: MeldingService,
) {
    val dialogmeldingFraBehandlerConsumer = DialogmeldingFraBehandlerConsumer(
        database = database,
        meldingService = meldingService
    )
    val consumerProperties =
        kafkaConsumerConfig<JacksonKafkaDeserializerDialogmeldingFraBehandler>(kafkaEnvironment).apply {
            this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
        }
    launchKafkaTask(
        applicationState = applicationState,
        topic = DIALOGMELDING_FROM_BEHANDLER_TOPIC,
        consumerProperties = consumerProperties,
        kafkaConsumerService = dialogmeldingFraBehandlerConsumer,
    )
}
