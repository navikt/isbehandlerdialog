package no.nav.syfo.testhelper

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.database.DatabaseEnvironment
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.client.*
import no.nav.syfo.client.azuread.AzureEnvironment

fun testEnvironment(
    azureOpenIdTokenEndpoint: String,
    syfoTilgangskontrollUrl: String,
    pdfGenClientUrl: String,
) = Environment(
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
        appWellKnownUrl = "wellknown",
        openidConfigTokenEndpoint = azureOpenIdTokenEndpoint,
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
            baseUrl = syfoTilgangskontrollUrl,
            clientId = "dev-fss.teamsykefravr.syfotilgangskontroll",
        ),
        dialogmeldingpdfgen = OpenClientEnvironment(
            baseUrl = pdfGenClientUrl
        )
    ),
    electorPath = "electorPath",
    produceBehandlerDialogmeldingBestilling = true,
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true,
)
