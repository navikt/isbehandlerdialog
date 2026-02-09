package no.nav.syfo.testhelper

import io.mockk.mockk
import no.nav.syfo.ApplicationState
import no.nav.syfo.application.MeldingService
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.client.dialogmelding.DialogmeldingClient
import no.nav.syfo.infrastructure.client.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.infrastructure.client.padm2.Padm2Client
import no.nav.syfo.infrastructure.client.pdfgen.PdfGenClient
import no.nav.syfo.infrastructure.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.client.wellknown.WellKnown
import no.nav.syfo.infrastructure.database.repository.MeldingRepository
import no.nav.syfo.testhelper.mock.mockHttpClient
import java.nio.file.Paths

fun wellKnownInternalAzureAD(): WellKnown {
    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    return WellKnown(
        issuer = "https://sts.issuer.net/veileder/v2",
        jwksUri = uri.toString()
    )
}

class ExternalMockEnvironment private constructor() {
    val applicationState: ApplicationState = testAppState()
    val database = TestDatabase()

    val environment = testEnvironment()

    val mockHttpClient = mockHttpClient(environment = environment)

    val wellKnownInternalAzureAD = wellKnownInternalAzureAD()

    val meldingRepository = MeldingRepository(database = database)

    val pdfgenClient = PdfGenClient(
        pdfGenBaseUrl = environment.clients.dialogmeldingpdfgen.baseUrl,
        legeerklaringPdfGenBaseUrl = environment.clients.dialogmeldingpdfgen.baseUrl,
        httpClient = mockHttpClient,
    )
    val azureAdClient = AzureAdClient(
        azureEnvironment = environment.azure,
        httpClient = mockHttpClient,
    )
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        azureAdClient = azureAdClient,
        clientEnvironment = environment.clients.istilgangskontroll,
        httpClient = mockHttpClient,
    )
    val dokarkivClient = DokarkivClient(
        azureAdClient = azureAdClient,
        clientEnvironment = environment.clients.dokarkiv,
        httpClient = mockHttpClient,
    )
    val dialogmeldingClient = DialogmeldingClient(
        azureAdClient = azureAdClient,
        clientEnvironment = environment.clients.dialogmelding,
        client = mockHttpClient,
    )
    val oppfolgingstilfelleClient = OppfolgingstilfelleClient(
        azureAdClient = azureAdClient,
        clientEnvironment = environment.clients.oppfolgingstilfelle,
        httpClient = mockHttpClient,
    )
    val padm2Client = Padm2Client(
        azureAdClient = azureAdClient,
        clientEnvironment = environment.clients.padm2,
        httpClient = mockHttpClient,
    )

    val meldingService = MeldingService(
        database = database,
        meldingRepository = meldingRepository,
        dialogmeldingBestillingProducer = mockk(),
        oppfolgingstilfelleClient = oppfolgingstilfelleClient,
        pdfgenClient = pdfgenClient,
        padm2Client = padm2Client,
    )

    companion object {
        val instance: ExternalMockEnvironment = ExternalMockEnvironment()
    }
}
