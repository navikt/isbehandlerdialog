package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.application.cronjob.cronjobModule
import no.nav.syfo.application.database.applicationDatabase
import no.nav.syfo.application.database.databaseModule
import no.nav.syfo.application.kafka.kafkaAivenProducerConfig
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.padm2.Padm2Client
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.client.wellknown.getWellKnown
import no.nav.syfo.melding.MeldingService
import no.nav.syfo.melding.kafka.*
import no.nav.syfo.melding.kafka.config.KafkaBehandlerDialogmeldingSerializer
import no.nav.syfo.melding.kafka.legeerklaring.launchKafkaTaskLegeerklaring
import no.nav.syfo.melding.status.kafka.launchKafkaTaskDialogmeldingStatus
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

const val applicationPort = 8080

fun main() {
    val applicationState = ApplicationState()
    val logger = LoggerFactory.getLogger("ktor.application")
    val environment = Environment()
    val wellKnownInternalAzureAD = getWellKnown(
        wellKnownUrl = environment.azure.appWellKnownUrl,
    )
    val azureAdClient = AzureAdClient(
        azureEnvironment = environment.azure
    )
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        azureAdClient = azureAdClient,
        clientEnvironment = environment.clients.syfotilgangskontroll
    )
    val padm2Client = Padm2Client(
        azureAdClient = azureAdClient,
        clientEnvironment = environment.clients.padm2,
    )
    val dialogmeldingBestillingProducer = DialogmeldingBestillingProducer(
        dialogmeldingBestillingKafkaProducer = KafkaProducer(
            kafkaAivenProducerConfig<KafkaBehandlerDialogmeldingSerializer>(
                kafkaEnvironment = environment.kafka,
            ),
        ),
    )
    val pdfgenClient = PdfGenClient(
        pdfGenBaseUrl = environment.clients.dialogmeldingpdfgen.baseUrl
    )
    lateinit var meldingService: MeldingService

    val applicationEngineEnvironment = applicationEngineEnvironment {
        log = logger
        config = HoconApplicationConfig(ConfigFactory.load())
        connector {
            port = applicationPort
        }
        module {
            databaseModule(
                databaseEnvironment = environment.database,
            )
            meldingService = MeldingService(
                database = applicationDatabase,
                pdfgenClient = pdfgenClient,
                dialogmeldingBestillingProducer = dialogmeldingBestillingProducer,
            )
            apiModule(
                applicationState = applicationState,
                database = applicationDatabase,
                environment = environment,
                wellKnownInternalAzureAD = wellKnownInternalAzureAD,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                meldingService = meldingService,
            )
            cronjobModule(
                applicationState = applicationState,
                database = applicationDatabase,
                environment = environment,
                azureAdClient = azureAdClient,
            )
        }
    }

    applicationEngineEnvironment.monitor.subscribe(ApplicationStarted) {
        applicationState.ready = true
        logger.info("Application is ready, running Java VM ${Runtime.version()}")
        launchKafkaTaskDialogmeldingFraBehandler(
            applicationState = applicationState,
            kafkaEnvironment = environment.kafka,
            database = applicationDatabase,
            padm2Client = padm2Client,
        )

        launchKafkaTaskDialogmeldingStatus(
            applicationState = applicationState,
            kafkaEnvironment = environment.kafka,
            database = applicationDatabase,
            meldingService = meldingService,
        )

        if (environment.toggleConsumeLegeerklaring) {
            launchKafkaTaskLegeerklaring(
                applicationState = applicationState,
                kafkaEnvironment = environment.kafka,
                database = applicationDatabase,
            )
        }
    }

    val server = embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment,
    ) {
        connectionGroupSize = 8
        workerGroupSize = 8
        callGroupSize = 16
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
        }
    )

    server.start(wait = true)
}
