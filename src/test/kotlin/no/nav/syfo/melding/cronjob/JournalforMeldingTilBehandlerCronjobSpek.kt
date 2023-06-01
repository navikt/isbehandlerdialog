package no.nav.syfo.melding.cronjob

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.cronjob.CronjobResult
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.client.dokarkiv.domain.*
import no.nav.syfo.melding.JournalforMeldingTilBehandlerService
import no.nav.syfo.melding.api.toMeldingTilBehandler
import no.nav.syfo.melding.database.*
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
                val meldingTilBehandler =
                    generateMeldingTilBehandlerRequestDTO().toMeldingTilBehandler(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                val journalpostId = 1
                val journalpostResponse = createJournalpostResponse().copy(
                    journalpostId = journalpostId,
                )
                val pdf = byteArrayOf(0x6b, 0X61, 0x6b, 0x65)
                val expectedJournalpostRequestMeldingTilBehandler = journalpostRequestGenerator(pdf, BrevkodeType.FORESPORSEL_OM_PASIENT)
                val expectedJournalpostRequestPaminnelse = journalpostRequestGenerator(pdf, BrevkodeType.FORESPORSEL_OM_PASIENT_PAMINNELSE)
                coEvery { dokarkivClient.journalfor(any()) } returns journalpostResponse

                database.connection.use { connection ->
                    val meldingId = connection.createMeldingTilBehandler(
                        meldingTilBehandler,
                        commit = false,
                    )
                    connection.createPdf(
                        pdf = pdf,
                        meldingId = meldingId,
                        commit = false,
                    )
                    val paminnelseId = connection.createMeldingTilBehandler(
                        generatePaminnelseRequestDTO().toMeldingTilBehandler(opprinneligMelding = meldingTilBehandler),
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
                result.updated shouldBeEqualTo 2
                result.failed shouldBeEqualTo 0
                meldinger.first().journalpostId shouldBeEqualTo journalpostId.toString()
                meldinger.last().journalpostId shouldBeEqualTo journalpostId.toString()

                coVerifyOrder {
                    dokarkivClient.journalfor(expectedJournalpostRequestMeldingTilBehandler)
                    dokarkivClient.journalfor(expectedJournalpostRequestPaminnelse)
                }
            }
        }
    }
})

fun createJournalpostResponse() = JournalpostResponse(
    dokumenter = null,
    journalpostId = 1,
    journalpostferdigstilt = null,
    journalstatus = "status",
    melding = null,
)
