package no.nav.syfo.application.cronjob

import io.ktor.server.application.*
import no.nav.syfo.application.*
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.client.leaderelection.LeaderPodClient
import no.nav.syfo.melding.JournalforMeldingTilBehandlerService
import no.nav.syfo.melding.cronjob.JournalforMeldingTilBehandlerCronjob
import no.nav.syfo.melding.cronjob.MeldingFraBehandlerCronjob
import no.nav.syfo.melding.kafka.KafkaMeldingFraBehandlerProducer
import no.nav.syfo.melding.kafka.PublishMeldingFraBehandlerService
import no.nav.syfo.melding.kafka.config.kafkaMeldingFraBehandlerProducerConfig

fun Application.cronjobModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    environment: Environment,
    azureAdClient: AzureAdClient,
) {
    val leaderPodClient = LeaderPodClient(
        electorPath = environment.electorPath
    )
    val cronjobRunner = CronjobRunner(
        applicationState = applicationState,
        leaderPodClient = leaderPodClient
    )

    val dokarkivClient = DokarkivClient(
        azureAdClient = azureAdClient,
        clientEnvironment = environment.clients.dokarkiv,
    )

    val journalforMeldingTilBehandlerService = JournalforMeldingTilBehandlerService(
        database = database,
    )

    val journalforMeldingTilBehandlerCronjob = JournalforMeldingTilBehandlerCronjob(
        dokarkivClient = dokarkivClient,
        journalforMeldingTilBehandlerService = journalforMeldingTilBehandlerService,
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
    listOf(journalforMeldingTilBehandlerCronjob, meldingFraBehandlerCronjob).forEach {
        launchBackgroundTask(
            applicationState = applicationState,
        ) {
            cronjobRunner.start(cronjob = it)
        }
    }
}
