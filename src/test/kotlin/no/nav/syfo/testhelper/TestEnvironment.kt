package no.nav.syfo.testhelper

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.api.access.PreAuthorizedClient
import no.nav.syfo.application.database.DatabaseEnvironment
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.client.*
import no.nav.syfo.client.azuread.AzureEnvironment
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
        syfotilgangskontroll = ClientEnvironment(
            baseUrl = "syfoTilgangskontrollUrl",
            clientId = "dev-fss.teamsykefravr.syfotilgangskontroll",
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
        dokarkiv = ClientEnvironment(
            baseUrl = "dokarkivUrl",
            clientId = "dev-fss.teamdokumenthandtering.dokarkiv-q1"
        )
    ),
    electorPath = "electorPath",
    cronjobUbesvartMeldingIntervalDelayMinutes = 60L * 4,
    cronjobUbesvartMeldingFristHours = 24L * 14,
    legeerklaringBucketName = "test_bucket",
    legeerklaringVedleggBucketName = "vedlegg_bucket",
    cronjobAvvistMeldingStatusIntervalDelayMinutes = 60L * 4,
    storeMeldingTilNAV = true,
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
