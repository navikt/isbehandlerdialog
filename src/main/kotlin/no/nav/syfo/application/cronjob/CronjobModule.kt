package no.nav.syfo.application.cronjob

import no.nav.syfo.aktivitetskrav.cronjob.JournalforDialogmeldingCronjob
import no.nav.syfo.application.*
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.leaderelection.LeaderPodClient

fun launchCronjobModule(
    applicationState: ApplicationState,
    environment: Environment,
    database: DatabaseInterface,
) {
    val leaderPodClient = LeaderPodClient(
        electorPath = environment.electorPath
    )
    val cronjobRunner = CronjobRunner(
        applicationState = applicationState,
        leaderPodClient = leaderPodClient
    )

    // TODO: Start Cronjob for journalføring
    val journalforDialogmeldingCronjob = JournalforDialogmeldingCronjob(
        database = database,
        dokarkivCLient = DokarkivClient,
    )
    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        cronjobRunner.start(cronjob = journalforDialogmeldingCronjob)
    }

}
