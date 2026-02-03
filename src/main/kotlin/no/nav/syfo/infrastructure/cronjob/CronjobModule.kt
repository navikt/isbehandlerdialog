package no.nav.syfo.infrastructure.cronjob

import io.ktor.server.application.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.application.IMeldingRepository
import no.nav.syfo.application.JournalforMeldingTilBehandlerService
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.client.dialogmelding.DialogmeldingClient
import no.nav.syfo.infrastructure.client.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.client.leaderelection.LeaderPodClient
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.kafka.config.KafkaMeldingDTOSerializer
import no.nav.syfo.infrastructure.kafka.config.kafkaAivenProducerConfig
import no.nav.syfo.infrastructure.kafka.config.kafkaMeldingFraBehandlerProducerConfig
import no.nav.syfo.infrastructure.kafka.config.kafkaUbesvartMeldingProducerConfig
import no.nav.syfo.infrastructure.kafka.producer.*
import no.nav.syfo.launchBackgroundTask
import org.apache.kafka.clients.producer.KafkaProducer

fun Application.cronjobModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    meldingRepository: IMeldingRepository,
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

    val dialogmeldingClient = DialogmeldingClient(
        azureAdClient = azureAdClient,
        clientEnvironment = environment.clients.dialogmelding,
    )

    val journalforMeldingTilBehandlerService = JournalforMeldingTilBehandlerService(
        database = database,
    )

    val journalforMeldingTilBehandlerCronjob = JournalforMeldingTilBehandlerCronjob(
        dokarkivClient = dokarkivClient,
        dialogmeldingClient = dialogmeldingClient,
        journalforMeldingTilBehandlerService = journalforMeldingTilBehandlerService,
        isJournalforingRetryEnabled = environment.isJournalforingRetryEnabled,
    )

    val kafkaMeldingFraBehandlerProducer = KafkaMeldingFraBehandlerProducer(
        kafkaMeldingFraBehandlerProducer = kafkaMeldingFraBehandlerProducerConfig(
            applicationEnvironmentKafka = environment.kafka,
        ),
    )

    val publishMeldingFraBehandlerService = PublishMeldingFraBehandlerService(
        meldingRepository = meldingRepository,
        kafkaMeldingFraBehandlerProducer = kafkaMeldingFraBehandlerProducer,
    )

    val meldingFraBehandlerCronjob = MeldingFraBehandlerCronjob(
        publishMeldingFraBehandlerService = publishMeldingFraBehandlerService,
    )

    val kafkaUbesvartMeldingProducer = KafkaUbesvartMeldingProducer(
        ubesvartMeldingKafkaProducer = kafkaUbesvartMeldingProducerConfig(
            applicationEnvironmentKafka = environment.kafka,
        )
    )

    val publishUbesvartMeldingService = PublishUbesvartMeldingService(
        meldingRepository = meldingRepository,
        kafkaUbesvartMeldingProducer = kafkaUbesvartMeldingProducer,
        fristHours = environment.cronjobUbesvartMeldingFristHours,
    )

    val ubesvartMeldingCronjob = UbesvartMeldingCronjob(
        publishUbesvartMeldingService = publishUbesvartMeldingService,
        intervalDelayMinutes = environment.cronjobUbesvartMeldingIntervalDelayMinutes,
    )

    val avvistMeldingProducer = AvvistMeldingProducer(
        kafkaProducer = KafkaProducer(
            kafkaAivenProducerConfig<KafkaMeldingDTOSerializer>(kafkaEnvironment = environment.kafka)
        )
    )
    val publishAvvistMeldingService = PublishAvvistMeldingService(
        database = database,
        avvistMeldingProducer = avvistMeldingProducer
    )
    val avvistMeldingStatusCronjob = AvvistMeldingCronjob(
        publishAvvistMeldingService = publishAvvistMeldingService,
        intervalDelayMinutes = environment.cronjobAvvistMeldingStatusIntervalDelayMinutes,
    )

    val allCronjobs = mutableListOf(
        journalforMeldingTilBehandlerCronjob,
        meldingFraBehandlerCronjob,
        ubesvartMeldingCronjob,
        avvistMeldingStatusCronjob,
    )

    allCronjobs.forEach {
        launchBackgroundTask(
            applicationState = applicationState,
        ) {
            cronjobRunner.start(cronjob = it)
        }
    }
}
