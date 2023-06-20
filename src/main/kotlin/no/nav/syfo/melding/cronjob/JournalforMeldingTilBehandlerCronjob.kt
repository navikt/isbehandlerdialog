package no.nav.syfo.melding.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.cronjob.Cronjob
import no.nav.syfo.application.cronjob.CronjobResult
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.melding.JournalforMeldingTilBehandlerService
import no.nav.syfo.melding.domain.MeldingType
import no.nav.syfo.melding.domain.toJournalpostRequest
import org.slf4j.LoggerFactory

class JournalforMeldingTilBehandlerCronjob(
    private val dokarkivClient: DokarkivClient,
    private val journalforMeldingTilBehandlerService: JournalforMeldingTilBehandlerService,
) : Cronjob {
    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() {
        val result = runJournalforDialogmeldingJob()
        log.info(
            "Completed journalføring of dialogmelding processing job with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    suspend fun runJournalforDialogmeldingJob(): CronjobResult {
        val journalforingResult = CronjobResult()

        val ikkeJournalforteMeldingerTilBehandler = journalforMeldingTilBehandlerService.getIkkeJournalforte()

        ikkeJournalforteMeldingerTilBehandler.forEach { (meldingTilBehandler, pdf) ->
            try {
                val journalpostRequest = if (meldingTilBehandler.type === MeldingType.FORESPORSEL_PASIENT_PAMINNELSE) {
                    meldingTilBehandler.toJournalpostRequest(pdf = pdf, tittel = "Påminnelse til behandler")
                } else {
                    meldingTilBehandler.toJournalpostRequest(pdf = pdf, tittel = "Dialogmelding til behandler", overstyrInnsynsregler = "VISES_MASKINELT_GODKJENT")
                }

                val journalpostId = dokarkivClient.journalfor(
                    journalpostRequest = journalpostRequest,
                )?.journalpostId?.toString()

                journalpostId?.let {
                    journalforMeldingTilBehandlerService.updateJournalpostId(
                        melding = meldingTilBehandler,
                        journalpostId = it,
                    )
                    journalforingResult.updated++
                } ?: throw RuntimeException("Failed to Journalfor dialogmelding: response missing JournalpostId")
            } catch (e: Exception) {
                log.error("Exception caught while attempting Journalforing of dialogmelding", e)
                journalforingResult.failed++
            }
        }
        return journalforingResult
    }

    companion object {
        private val log = LoggerFactory.getLogger(JournalforMeldingTilBehandlerCronjob::class.java)
    }
}
