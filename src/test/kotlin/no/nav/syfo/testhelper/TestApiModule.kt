package no.nav.syfo.testhelper

import io.ktor.server.application.*
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.client.azuread.AzureAdClient

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
) {
    val azureAdClient = AzureAdClient(
        azureEnvironment = externalMockEnvironment.environment.azure,
    )
    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        database = externalMockEnvironment.database,
        environment = externalMockEnvironment.environment,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
        azureAdClient = azureAdClient,
    )
}
