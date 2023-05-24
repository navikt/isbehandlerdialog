package no.nav.syfo.testhelper

import io.ktor.server.application.*
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.melding.MeldingService
import no.nav.syfo.melding.kafka.DialogmeldingBestillingProducer

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    dialogmeldingBestillingProducer: DialogmeldingBestillingProducer,
) {
    val azureAdClient = AzureAdClient(
        azureEnvironment = externalMockEnvironment.environment.azure,
    )
    val pdfgenClient = PdfGenClient(
        pdfGenBaseUrl = externalMockEnvironment.environment.clients.dialogmeldingpdfgen.baseUrl
    )
    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        database = externalMockEnvironment.database,
        environment = externalMockEnvironment.environment,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
        azureAdClient = azureAdClient,
        meldingService = MeldingService(
            database = externalMockEnvironment.database,
            dialogmeldingBestillingProducer = dialogmeldingBestillingProducer,
            pdfgenClient = pdfgenClient,
        )
    )
}
