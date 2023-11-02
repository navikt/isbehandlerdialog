package no.nav.syfo.testhelper

import io.ktor.server.application.*
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.melding.MeldingService
import no.nav.syfo.melding.kafka.producer.DialogmeldingBestillingProducer

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    dialogmeldingBestillingProducer: DialogmeldingBestillingProducer,
) {
    val mockHttpClient = externalMockEnvironment.mockHttpClient
    val azureAdClient = AzureAdClient(
        azureEnvironment = externalMockEnvironment.environment.azure,
        httpClient = mockHttpClient,
    )
    val pdfgenClient = PdfGenClient(
        pdfGenBaseUrl = externalMockEnvironment.environment.clients.dialogmeldingpdfgen.baseUrl,
        legeerklaringPdfGenBaseUrl = externalMockEnvironment.environment.clients.dialogmeldingpdfgen.baseUrl,
        httpClient = mockHttpClient,
    )
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        azureAdClient = azureAdClient,
        clientEnvironment = externalMockEnvironment.environment.clients.istilgangskontroll,
        httpClient = mockHttpClient,
    )
    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        database = externalMockEnvironment.database,
        environment = externalMockEnvironment.environment,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
        veilederTilgangskontrollClient = veilederTilgangskontrollClient,
        meldingService = MeldingService(
            database = externalMockEnvironment.database,
            dialogmeldingBestillingProducer = dialogmeldingBestillingProducer,
            pdfgenClient = pdfgenClient,
        )
    )
}
