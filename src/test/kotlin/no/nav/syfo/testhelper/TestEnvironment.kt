package no.nav.syfo.testhelper

import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.api.access.PreAuthorizedClient
import no.nav.syfo.infrastructure.client.ClientEnvironment
import no.nav.syfo.infrastructure.client.ClientsEnvironment
import no.nav.syfo.infrastructure.client.OpenClientEnvironment
import no.nav.syfo.infrastructure.client.azuread.AzureEnvironment
import no.nav.syfo.infrastructure.database.DatabaseEnvironment
import no.nav.syfo.infrastructure.kafka.config.KafkaEnvironment
import no.nav.syfo.util.configuredJacksonMapper

fun testEnvironment() = Environment(
    database = DatabaseEnvironment(
        host = "localhost",
        port = "5432",
        name = "isbehandlerdialog_dev",
        username = "username",
        password = "password",
    ),
    azure = AzureEnvironment(
        appClientId = "isbehandlerdialog-client-id",
        appClientSecret = "isbehandlerdialog-secret",
        appPreAuthorizedApps = configuredJacksonMapper().writeValueAsString(testAzureAppPreAuthorizedApps),
        appWellKnownUrl = "wellknown",
        openidConfigTokenEndpoint = "azureOpenIdTokenEndpoint",
    ),
    kafka = KafkaEnvironment(
        aivenBootstrapServers = "kafkaBootstrapServers",
        aivenCredstorePassword = "credstorepassord",
        aivenKeystoreLocation = "keystore",
        aivenSecurityProtocol = "SSL",
        aivenTruststoreLocation = "truststore",
        aivenSchemaRegistryUrl = "http://kafka-schema-registry.tpa.svc.nais.local:8081",
        aivenRegistryUser = "registryuser",
        aivenRegistryPassword = "registrypassword",
    ),
    clients = ClientsEnvironment(
        istilgangskontroll = ClientEnvironment(
            baseUrl = "istilgangskontrollUrl",
            clientId = "dev-fss.teamsykefravr.istilgangskontroll",
        ),
        dialogmeldingpdfgen = OpenClientEnvironment(
            baseUrl = "pdfGenClientUrl"
        ),
        legeerklaringpdfgen = OpenClientEnvironment(
            baseUrl = "pdfGenClientUrl"
        ),
        padm2 = ClientEnvironment(
            baseUrl = "padm2Url",
            clientId = "dev-gcp.teamsykefravr.padm2",
        ),
        oppfolgingstilfelle = ClientEnvironment(
            baseUrl = "oppfolgingstilfelleUrl",
            clientId = "dev-gcp.teamsykefravr.isoppfolgingstilfelle",
        ),
        dokarkiv = ClientEnvironment(
            baseUrl = "dokarkivUrl",
            clientId = "dev-fss.teamdokumenthandtering.dokarkiv-q1"
        ),
        dialogmelding = ClientEnvironment(
            baseUrl = "dialogmeldingUrl",
            clientId = "dev-gcp.teamsykefravr.isdialogmelding",
        ),
    ),
    isJournalforingRetryEnabled = true,
    electorPath = "electorPath",
    cronjobUbesvartMeldingIntervalDelayMinutes = 60L * 4,
    cronjobUbesvartMeldingFristHours = 24L * 21,
    legeerklaringBucketName = "test_bucket",
    legeerklaringVedleggBucketName = "vedlegg_bucket",
    cronjobAvvistMeldingStatusIntervalDelayMinutes = 60L * 4,
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true,
)

private const val padm2ApplicationName: String = "padm2"
const val padm2ClientId = "$padm2ApplicationName-client-id"

val testAzureAppPreAuthorizedApps = listOf(
    PreAuthorizedClient(
        name = "cluster:teamsykefravr:$padm2ApplicationName",
        clientId = padm2ClientId,
    ),
)
