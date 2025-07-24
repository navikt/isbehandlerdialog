package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.api.apiModule
import no.nav.syfo.application.MeldingService
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.infrastructure.client.padm2.Padm2Client
import no.nav.syfo.infrastructure.client.pdfgen.PdfGenClient
import no.nav.syfo.infrastructure.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.client.wellknown.getWellKnown
import no.nav.syfo.infrastructure.cronjob.cronjobModule
import no.nav.syfo.infrastructure.database.applicationDatabase
import no.nav.syfo.infrastructure.database.databaseModule
import no.nav.syfo.infrastructure.database.repository.MeldingRepository
import no.nav.syfo.infrastructure.kafka.config.KafkaBehandlerDialogmeldingSerializer
import no.nav.syfo.infrastructure.kafka.config.kafkaAivenProducerConfig
import no.nav.syfo.infrastructure.kafka.dialogmelding.launchKafkaTaskDialogmeldingFraBehandler
import no.nav.syfo.infrastructure.kafka.identhendelse.launchKafkaTaskIdenthendelse
import no.nav.syfo.infrastructure.kafka.legeerklaring.launchKafkaTaskLegeerklaring
import no.nav.syfo.infrastructure.kafka.producer.DialogmeldingBestillingProducer
import no.nav.syfo.infrastructure.kafka.launchKafkaTaskDialogmeldingStatus
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
        clientEnvironment = environment.clients.istilgangskontroll
    )
    val padm2Client = Padm2Client(
        azureAdClient = azureAdClient,
        clientEnvironment = environment.clients.padm2,
    )
    val oppfolgingstilfelleClient = OppfolgingstilfelleClient(
        azureAdClient = azureAdClient,
        clientEnvironment = environment.clients.oppfolgingstilfelle,
    )
    val dialogmeldingBestillingProducer = DialogmeldingBestillingProducer(
        dialogmeldingBestillingKafkaProducer = KafkaProducer(
            kafkaAivenProducerConfig<KafkaBehandlerDialogmeldingSerializer>(
                kafkaEnvironment = environment.kafka,
            ),
        ),
    )
    val pdfgenClient = PdfGenClient(
        pdfGenBaseUrl = environment.clients.dialogmeldingpdfgen.baseUrl,
        legeerklaringPdfGenBaseUrl = environment.clients.legeerklaringpdfgen.baseUrl,
    )
    val meldingRepository = MeldingRepository(database = applicationDatabase)
    lateinit var meldingService: MeldingService

    val applicationEngineEnvironment = applicationEnvironment {
        log = logger
        config = HoconApplicationConfig(ConfigFactory.load())
    }
    val server = embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment,
        configure = {
            connector {
                port = applicationPort
            }
            connectionGroupSize = 8
            workerGroupSize = 8
            callGroupSize = 16
        },
        module = {
            databaseModule(
                databaseEnvironment = environment.database,
            )
            meldingService = MeldingService(
                database = applicationDatabase,
                meldingRepository = meldingRepository,
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

            monitor.subscribe(ApplicationStarted) {
                applicationState.ready = true
                logger.info("Application is ready, running Java VM ${Runtime.version()}")

                launchKafkaTaskDialogmeldingFraBehandler(
                    applicationState = applicationState,
                    kafkaEnvironment = environment.kafka,
                    database = applicationDatabase,
                    padm2Client = padm2Client,
                    oppfolgingstilfelleClient = oppfolgingstilfelleClient,
                )

                launchKafkaTaskDialogmeldingStatus(
                    applicationState = applicationState,
                    kafkaEnvironment = environment.kafka,
                    database = applicationDatabase,
                    meldingRepository = meldingRepository,
                    meldingService = meldingService,
                )

                launchKafkaTaskLegeerklaring(
                    applicationState = applicationState,
                    kafkaEnvironment = environment.kafka,
                    bucketName = environment.legeerklaringBucketName,
                    bucketNameVedlegg = environment.legeerklaringVedleggBucketName,
                    database = applicationDatabase,
                    pdfgenClient = pdfgenClient,
                )

                launchKafkaTaskIdenthendelse(
                    applicationState = applicationState,
                    kafkaEnvironment = environment.kafka,
                    database = applicationDatabase,
                )
            }
        }
    )

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(10, 10, TimeUnit.SECONDS)
        }
    )

    server.start(wait = true)
}
