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
import no.nav.syfo.testhelper.generator.journalpostRequestGenerator
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.util.*

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
                val journalpostId = 1
                val journalpostResponse = createJournalpostResponse().copy(
                    journalpostId = journalpostId,
                )
                val pdf = byteArrayOf(0x6b, 0X61, 0x6b, 0x65)
                val journalpostRequest = journalpostRequestGenerator(pdf)
                coEvery { dokarkivClient.journalfor(any()) } returns journalpostResponse
                val meldingId = database.connection.createMeldingTilBehandler(
                    createMeldingTilBehandler(),
                )
                database.connection.createPdf(
                    pdf = pdf,
                    meldingId = meldingId,
                )

                var result: CronjobResult
                runBlocking {
                    result = journalforDialogmeldingCronjob.runJournalforDialogmeldingJob()
                }

                val melding = database.getMeldingerForArbeidstaker(
                    arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                )[0]
                result.updated shouldBeEqualTo 1
                result.failed shouldBeEqualTo 0
                melding.journalpostId shouldBeEqualTo journalpostId.toString()
                coVerify(exactly = 1) { dokarkivClient.journalfor(journalpostRequest) }
            }
        }
    }
})

fun createMeldingTilBehandler() = MeldingTilBehandler(
    uuid = UUID.randomUUID(),
    createdAt = OffsetDateTime.now(),
    type = MeldingType.FORESPORSEL_PASIENT,
    conversationRef = UUID.randomUUID(),
    parentRef = null,
    tidspunkt = OffsetDateTime.now(),
    arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
    behandlerPersonIdent = UserConstants.BEHANDLER_PERSONIDENT,
    behandlerNavn = UserConstants.BEHANDLER_NAVN,
    behandlerRef = UUID.randomUUID(),
    tekst = "",
    document = emptyList(),
    antallVedlegg = 0,
    ubesvartPublishedAt = null,
)

fun createJournalpostResponse() = JournalpostResponse(
    dokumenter = null,
    journalpostId = 1,
    journalpostferdigstilt = null,
    journalstatus = "status",
    melding = null,
)
