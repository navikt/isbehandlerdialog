package no.nav.syfo.melding.kafka

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.application.launchBackgroundTask
import no.nav.syfo.client.padm2.Padm2Client

fun launchKafkaTaskDialogmeldingFraBehandler(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    database: DatabaseInterface,
    padm2Client: Padm2Client,
) {
    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        blockingApplicationLogicDialogmeldingFraBehandler(
            applicationState = applicationState,
            kafkaEnvironment = kafkaEnvironment,
            database = database,
            padm2Client = padm2Client,
        )
    }
}
