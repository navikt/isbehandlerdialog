package no.nav.syfo.melding.cronjob

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.cronjob.CronjobResult
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.client.dokarkiv.domain.*
import no.nav.syfo.melding.JournalforMeldingTilBehandlerService
import no.nav.syfo.melding.database.*
import no.nav.syfo.melding.domain.MeldingTilBehandler
import no.nav.syfo.melding.domain.MeldingType
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class JournalforDialogmeldingCronjobSpek : Spek({
    with(TestApplicationEngine()) {
        start()
        val database = ExternalMockEnvironment.instance.database

        val dokarkivClient = mockk<DokarkivClient>()
        val journalforMeldingTilBehandlerService = JournalforMeldingTilBehandlerService(
            database = database,
        )
        val journalforDialogmeldingCronjob = JournalforMeldingTilBehandlerCronjob(
            dokarkivClient = dokarkivClient,
            journalforMeldingTilBehandlerService = journalforMeldingTilBehandlerService,
        )

        beforeEachTest {
            database.dropData()
        }

        describe("JournalforMeldingTilBehandlerCronjob") {
            afterEachTest {
                database.dropData()
            }

            it("Journalfør and update melding in database for each melding that's not journalført") {
                val meldingTilBehandlerTilleggsopplysninger = defaultMeldingTilBehandler
                val meldingTilBehandlerLegeerklaring =
                    generateMeldingTilBehandler(type = MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING)
                val meldingTilBehandlerPaminnelse = MeldingTilBehandler.createForesporselPasientPaminnelse(
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
                val expectedJournalpostRequestMeldingTilBehandlerLegeerklaring = expectedJournalpostRequestMeldingTilBehandlerTilleggsopplysninger
                    .copy(
                        eksternReferanseId = meldingTilBehandlerLegeerklaring.uuid.toString()
                    )
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
                runBlocking {
                    result = journalforDialogmeldingCronjob.runJournalforDialogmeldingJob()
                }

                val meldinger = database.getMeldingerForArbeidstaker(
                    arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                )
                result.updated shouldBeEqualTo 3
                result.failed shouldBeEqualTo 0
                meldinger.forEach {
                    it.journalpostId shouldBeEqualTo journalpostId.toString()
                }

                coVerifyAll {
                    dokarkivClient.journalfor(expectedJournalpostRequestMeldingTilBehandlerTilleggsopplysninger)
                    dokarkivClient.journalfor(expectedJournalpostRequestMeldingTilBehandlerLegeerklaring)
                    dokarkivClient.journalfor(expectedJournalpostRequestPaminnelse)
                }
            }
        }
    }
})

fun createJournalpostResponse(journalpostId: Int) = JournalpostResponse(
    dokumenter = null,
    journalpostId = journalpostId,
    journalpostferdigstilt = null,
    journalstatus = "status",
    melding = null,
)
