package no.nav.syfo.client.dokarkiv

import kotlinx.coroutines.test.runTest
import no.nav.syfo.infrastructure.client.dokarkiv.domain.BrevkodeType
import no.nav.syfo.infrastructure.client.dokarkiv.domain.MeldingTittel
import no.nav.syfo.infrastructure.client.dokarkiv.domain.OverstyrInnsynsregler
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.generator.journalpostRequestGenerator
import no.nav.syfo.testhelper.mock.conflictResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import java.util.*

class DokarkivClientTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val dokarkivClient = externalMockEnvironment.dokarkivClient
    val journalpostRequest = journalpostRequestGenerator(
        pdf = UserConstants.PDF_LEGEERKLARING,
        brevkodeType = BrevkodeType.FORESPORSEL_OM_PASIENT,
        tittel = MeldingTittel.DIALOGMELDING_DEFAULT.value,
        overstyrInnsynsregler = OverstyrInnsynsregler.VISES_MASKINELT_GODKJENT.value,
        eksternReferanseId = UUID.randomUUID().toString(),
    )

    @Test
    fun `returns OK response when unique eksternReferanseId`() = runTest {
        val response =
            dokarkivClient.journalfor(journalpostRequest = journalpostRequest)

        assertNotNull(response)
    }

    @Test
    fun `returns existing journalpostResponse when conflicting eksternReferanseId`() = runTest {
        val journalpostRequestWithConflictingEksternReferanseId =
            journalpostRequest.copy(eksternReferanseId = UserConstants.EXISTING_EKSTERN_REFERANSE_UUID)

        val response =
            dokarkivClient.journalfor(journalpostRequest = journalpostRequestWithConflictingEksternReferanseId)

        assertEquals(conflictResponse.journalpostId, response?.journalpostId)
        assertEquals(conflictResponse.journalstatus, response?.journalstatus)
    }
}
