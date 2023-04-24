package no.nav.syfo.application.cronjob

import no.nav.syfo.application.*
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.client.leaderelection.LeaderPodClient
import no.nav.syfo.melding.cronjob.JournalforDialogmeldingCronjob
import no.nav.syfo.melding.cronjob.MeldingFraBehandlerCronjob
import no.nav.syfo.melding.kafka.KafkaMeldingFraBehandlerProducer
import no.nav.syfo.melding.kafka.PublishMeldingFraBehandlerService
import no.nav.syfo.melding.kafka.config.kafkaMeldingFraBehandlerProducerConfig

fun launchCronjobModule(
    applicationState: ApplicationState,
    environment: Environment,
    database: DatabaseInterface,
    dokarkivClient: DokarkivClient,
) {
    val leaderPodClient = LeaderPodClient(
        electorPath = environment.electorPath
    )
    val cronjobRunner = CronjobRunner(
        applicationState = applicationState,
        leaderPodClient = leaderPodClient
    )

    val journalforDialogmeldingCronjob = JournalforDialogmeldingCronjob(
        database = database,
        dokarkivCLient = dokarkivClient,
    )

    val kafkaMeldingFraBehandlerProducer = KafkaMeldingFraBehandlerProducer(
        kafkaMeldingFraBehandlerProducer = kafkaMeldingFraBehandlerProducerConfig(
            applicationEnvironmentKafka = environment.kafka,
        ),
    )

    val publishMeldingFraBehandlerService = PublishMeldingFraBehandlerService(
        database = database,
        kafkaMeldingFraBehandlerProducer = kafkaMeldingFraBehandlerProducer,
    )

    val meldingFraBehandlerCronjob = MeldingFraBehandlerCronjob(
        publishMeldingFraBehandlerService = publishMeldingFraBehandlerService,
    )

    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        cronjobRunner.start(cronjob = journalforDialogmeldingCronjob)
    }

    if (environment.produceMeldingFraBehandlerCronjob) {
        launchBackgroundTask(applicationState = applicationState) {
            cronjobRunner.start(cronjob = meldingFraBehandlerCronjob)
        }
    }
}
