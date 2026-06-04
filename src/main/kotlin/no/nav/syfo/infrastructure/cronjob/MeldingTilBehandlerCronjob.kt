package no.nav.syfo.infrastructure.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.IMeldingRepository
import no.nav.syfo.infrastructure.kafka.producer.DialogmeldingBestillingProducer
import org.slf4j.LoggerFactory

class MeldingTilBehandlerCronjob(
    val dialogmeldingBestillingProducer: DialogmeldingBestillingProducer,
    val meldingRepository: IMeldingRepository,
) : Cronjob {
    override val initialDelayMinutes: Long = 3
    override val intervalDelayMinutes: Long = 2

    override suspend fun run() {
        val result = runJob()
        log.info(
            "Completed publishing meldingTilBehandler processing job with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    fun runJob(): CronjobResult {
        val result = CronjobResult()
        val unpublishedMeldingerTilBehandler = meldingRepository.getUnpublishedMeldingerTilBehandler()

        unpublishedMeldingerTilBehandler.forEach { pair ->
            try {
                dialogmeldingBestillingProducer.sendDialogmeldingBestilling(
                    meldingTilBehandler = pair.first,
                    meldingPdf = pair.second,
                )
                meldingRepository.updateUtgaendePublishedAt(pair.first.uuid)
                result.updated++
            } catch (e: Exception) {
                log.error("Caught exception in publish meldingTilBehandler", e)
                result.failed++
            }
        }

        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(MeldingTilBehandlerCronjob::class.java)
    }
}
