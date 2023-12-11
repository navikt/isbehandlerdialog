package no.nav.syfo.client.dokarkiv

import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.dokarkiv.domain.BrevkodeType
import no.nav.syfo.client.dokarkiv.domain.MeldingTittel
import no.nav.syfo.client.dokarkiv.domain.OverstyrInnsynsregler
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.generator.journalpostRequestGenerator
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID

val journalpostRequest = journalpostRequestGenerator(
    pdf = UserConstants.PDF_LEGEERKLARING,
    brevkodeType = BrevkodeType.FORESPORSEL_OM_PASIENT,
    tittel = MeldingTittel.DIALOGMELDING_DEFAULT.value,
    overstyrInnsynsregler = OverstyrInnsynsregler.VISES_MASKINELT_GODKJENT.value,
    eksternReferanseId = UUID.randomUUID().toString(),
)

class DokarkivClientSpek : Spek({

    val externalMockEnvironment = ExternalMockEnvironment.instance
    val mockHttpClient = externalMockEnvironment.mockHttpClient
    val azureAdClient = AzureAdClient(
        azureEnvironment = externalMockEnvironment.environment.azure,
        httpClient = mockHttpClient,
    )

    val dokarkivClient = DokarkivClient(
        azureAdClient = azureAdClient,
        clientEnvironment = externalMockEnvironment.environment.clients.dokarkiv,
        httpClient = mockHttpClient,
    )

    describe("${DokarkivClient::class.java.simpleName} journalfor") {
        it("returns OK response when unique eksternReferanseId") {
            val response = runBlocking {
                dokarkivClient.journalfor(journalpostRequest = journalpostRequest)
            }

            response.shouldNotBeNull()
        }

        it("returns null response when conflicting eksternReferanseId") {
            val journalpostRequestWithConflictingEksternReferanseId =
                journalpostRequest.copy(eksternReferanseId = UserConstants.EXISTING_EKSTERN_REFERANSE_UUID)

            val response = runBlocking {
                dokarkivClient.journalfor(journalpostRequest = journalpostRequestWithConflictingEksternReferanseId)
            }

            response.shouldBeNull()
        }
    }
})
