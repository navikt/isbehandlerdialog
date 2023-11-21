package no.nav.syfo.application.cronjob

import io.ktor.server.application.*
import no.nav.syfo.application.*
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.kafkaAivenProducerConfig
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.client.leaderelection.LeaderPodClient
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.melding.JournalforMeldingTilBehandlerService
import no.nav.syfo.melding.cronjob.*
import no.nav.syfo.melding.kafka.config.KafkaMeldingDTOSerializer
import no.nav.syfo.melding.kafka.config.kafkaMeldingFraBehandlerProducerConfig
import no.nav.syfo.melding.kafka.config.kafkaUbesvartMeldingProducerConfig
import no.nav.syfo.melding.kafka.producer.*
import org.apache.kafka.clients.producer.KafkaProducer

fun Application.cronjobModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    environment: Environment,
    azureAdClient: AzureAdClient,
    bucketName: String,
    pdfgenClient: PdfGenClient,
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

    val kafkaUbesvartMeldingProducer = KafkaUbesvartMeldingProducer(
        ubesvartMeldingKafkaProducer = kafkaUbesvartMeldingProducerConfig(
            applicationEnvironmentKafka = environment.kafka,
        )
    )

    val publishUbesvartMeldingService = PublishUbesvartMeldingService(
        database = database,
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
    val repairLegeerklaringCronjob = RepairLegeerklaringCronjob(
        database = database,
        bucketName = bucketName,
        pdfgenClient = pdfgenClient,
    )

    val allCronjobs = mutableListOf(
        journalforMeldingTilBehandlerCronjob,
        meldingFraBehandlerCronjob,
        ubesvartMeldingCronjob,
        avvistMeldingStatusCronjob,
        repairLegeerklaringCronjob
    )

    allCronjobs.forEach {
        launchBackgroundTask(
            applicationState = applicationState,
        ) {
            cronjobRunner.start(cronjob = it)
        }
    }
}
