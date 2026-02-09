package no.nav.syfo.melding.cronjob

import io.mockk.coEvery
import io.mockk.coVerifyAll
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.syfo.application.JournalforMeldingTilBehandlerService
import no.nav.syfo.domain.Melding
import no.nav.syfo.domain.MeldingType
import no.nav.syfo.infrastructure.client.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.client.dokarkiv.domain.BrevkodeType
import no.nav.syfo.infrastructure.client.dokarkiv.domain.JournalpostResponse
import no.nav.syfo.infrastructure.client.dokarkiv.domain.MeldingTittel
import no.nav.syfo.infrastructure.client.dokarkiv.domain.OverstyrInnsynsregler
import no.nav.syfo.infrastructure.cronjob.CronjobResult
import no.nav.syfo.infrastructure.cronjob.JournalforMeldingTilBehandlerCronjob
import no.nav.syfo.infrastructure.database.createMeldingTilBehandler
import no.nav.syfo.infrastructure.database.createPdf
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.defaultMeldingTilBehandler
import no.nav.syfo.testhelper.generator.generateMeldingTilBehandler
import no.nav.syfo.testhelper.generator.generatePaminnelseRequestDTO
import no.nav.syfo.testhelper.generator.journalpostRequestGenerator
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JournalforDialogmeldingCronjobTest {

    private val database = ExternalMockEnvironment.instance.database
    private val meldingRepository = ExternalMockEnvironment.instance.meldingRepository
    private val dokarkivClient = mockk<DokarkivClient>()
    private val journalforMeldingTilBehandlerService = JournalforMeldingTilBehandlerService(
        database = database,
    )
    private val dialogmeldingClient = ExternalMockEnvironment.instance.dialogmeldingClient
    private val journalforDialogmeldingCronjob = JournalforMeldingTilBehandlerCronjob(
        dokarkivClient = dokarkivClient,
        dialogmeldingClient = dialogmeldingClient,
        journalforMeldingTilBehandlerService = journalforMeldingTilBehandlerService,
        isJournalforingRetryEnabled = ExternalMockEnvironment.instance.environment.isJournalforingRetryEnabled,
    )

    @AfterEach
    fun afterEach() {
        database.dropData()
    }

    @Test
    fun `Journalfør and update melding in database for each melding that's not journalført`() = runTest {
        val meldingTilBehandlerTilleggsopplysninger = defaultMeldingTilBehandler
        val meldingTilBehandlerLegeerklaring =
            generateMeldingTilBehandler(type = MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING)
        val meldingTilBehandlerPaminnelse = Melding.MeldingTilBehandler.createForesporselPasientPaminnelse(
            opprinneligMelding = meldingTilBehandlerTilleggsopplysninger,
            veilederIdent = UserConstants.VEILEDER_IDENT,
            document = generatePaminnelseRequestDTO().document,
        )

        val journalpostId = 1
        val journalpostResponse = createJournalpostResponse(journalpostId)
        val pdf = byteArrayOf(0x6b, 0X61, 0x6b, 0x65)
        val expectedJournalpostRequestMeldingTilBehandlerTilleggsopplysninger = journalpostRequestGenerator(
            pdf = pdf,
            brevkodeType = BrevkodeType.FORESPORSEL_OM_PASIENT,
            tittel = MeldingTittel.DIALOGMELDING_DEFAULT.value,
            overstyrInnsynsregler = OverstyrInnsynsregler.VISES_MASKINELT_GODKJENT.value,
            eksternReferanseId = meldingTilBehandlerTilleggsopplysninger.uuid.toString(),
        )
        val expectedJournalpostRequestMeldingTilBehandlerLegeerklaring =
            expectedJournalpostRequestMeldingTilBehandlerTilleggsopplysninger
                .copy(eksternReferanseId = meldingTilBehandlerLegeerklaring.uuid.toString())
        val expectedJournalpostRequestPaminnelse = journalpostRequestGenerator(
            pdf = pdf,
            brevkodeType = BrevkodeType.FORESPORSEL_OM_PASIENT_PAMINNELSE,
            tittel = MeldingTittel.DIALOGMELDING_PAMINNELSE.value,
            overstyrInnsynsregler = null,
            eksternReferanseId = meldingTilBehandlerPaminnelse.uuid.toString(),
        )

        coEvery { dokarkivClient.journalfor(any()) } returns journalpostResponse

        database.connection.use { connection ->
            listOf(
                meldingTilBehandlerTilleggsopplysninger,
                meldingTilBehandlerLegeerklaring
            ).forEach { melding ->
                val meldingId = connection.createMeldingTilBehandler(
                    melding,
                    commit = false,
                )
                connection.createPdf(
                    pdf = pdf,
                    meldingId = meldingId,
                    commit = false,
                )
            }
            val paminnelseId = connection.createMeldingTilBehandler(
                meldingTilBehandler = meldingTilBehandlerPaminnelse,
                commit = false,
            )
            connection.createPdf(
                pdf = pdf,
                meldingId = paminnelseId,
            )
        }

        var result: CronjobResult
        result = journalforDialogmeldingCronjob.runJournalforDialogmeldingJob()

        val meldinger = meldingRepository.getMeldingerForArbeidstaker(arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT)
        assertEquals(3, result.updated)
        assertEquals(0, result.failed)
        meldinger.forEach {
            assertEquals(journalpostId.toString(), it.journalpostId)
        }

        coVerifyAll {
            dokarkivClient.journalfor(expectedJournalpostRequestMeldingTilBehandlerTilleggsopplysninger)
            dokarkivClient.journalfor(expectedJournalpostRequestMeldingTilBehandlerLegeerklaring)
            dokarkivClient.journalfor(expectedJournalpostRequestPaminnelse)
        }
    }
}

fun createJournalpostResponse(journalpostId: Int) = JournalpostResponse(
    dokumenter = null,
    journalpostId = journalpostId,
    journalpostferdigstilt = null,
    journalstatus = "status",
    melding = null,
)
