package no.nav.syfo.melding.kafka

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.application.launchBackgroundTask

fun launchKafkaTaskDialogmeldingFraBehandler(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    database: DatabaseInterface,
) {
    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        blockingApplicationLogicDialogmeldingFraBehandler(
            applicationState = applicationState,
            kafkaEnvironment = kafkaEnvironment,
            database = database,
        )
    }
}
