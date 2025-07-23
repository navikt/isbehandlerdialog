package no.nav.syfo.infrastructure.kafka.dialogmelding

import no.nav.syfo.ApplicationState
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.infrastructure.client.padm2.Padm2Client
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
    padm2Client: Padm2Client,
    oppfolgingstilfelleClient: OppfolgingstilfelleClient,
) {
    val kafkaDialogmeldingFraBehandlerConsumer = KafkaDialogmeldingFraBehandlerConsumer(
        database = database,
        padm2Client = padm2Client,
        oppfolgingstilfelleClient = oppfolgingstilfelleClient,
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
