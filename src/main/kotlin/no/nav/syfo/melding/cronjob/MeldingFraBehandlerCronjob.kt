package no.nav.syfo.melding.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.cronjob.Cronjob
import no.nav.syfo.application.cronjob.CronjobResult
import no.nav.syfo.melding.kafka.PublishMeldingFraBehandlerService
import org.slf4j.LoggerFactory

class MeldingFraBehandlerCronjob(
    val publishMeldingFraBehandlerService: PublishMeldingFraBehandlerService,
) : Cronjob {
    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() {
        val result = runJob()
        log.info(
            "Completed publishing meldingFraBehandler processing job with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    fun runJob(): CronjobResult {
        val result = CronjobResult()
        val unpublishedMeldingerFraBehandler = publishMeldingFraBehandlerService.getUnpublishedMeldingerFraBehandler()

        unpublishedMeldingerFraBehandler.forEach { meldingFraBehandler ->
            try {
                publishMeldingFraBehandlerService.publishMeldingFraBehandler(meldingFraBehandler)
                result.updated++
            } catch (e: Exception) {
                log.error("Caught exception in publish meldingFraBehandler", e)
                result.failed++
            }
        }

        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(MeldingFraBehandlerCronjob::class.java)
    }
}
