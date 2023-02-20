package no.nav.syfo.dialogmelding.kafka

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.application.launchBackgroundTask

fun launchKafkaTaskDialogmeldingFromBehandler(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    database: DatabaseInterface,
) {
    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        blockingApplicationLogicDialogmeldingFromBehandler(
            applicationState = applicationState,
            kafkaEnvironment = kafkaEnvironment,
            database = database,
        )
    }
}
