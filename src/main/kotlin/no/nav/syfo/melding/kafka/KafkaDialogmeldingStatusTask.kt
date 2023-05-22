package no.nav.syfo.melding.kafka

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.KafkaEnvironment

const val DIALOGMELDING_STATUS_TOPIC = "teamsykefravr.behandler-dialogmelding-status"

fun launchKafkaTaskDialogmeldingStatus(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    database: DatabaseInterface
) {
    // TODO: Implement kafka consumer for dialogmelding status
}
