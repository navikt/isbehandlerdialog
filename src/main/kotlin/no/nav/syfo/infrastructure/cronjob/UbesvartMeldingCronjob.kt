package no.nav.syfo.infrastructure.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.infrastructure.kafka.producer.PublishUbesvartMeldingService
import org.slf4j.LoggerFactory

class UbesvartMeldingCronjob(
    private val publishUbesvartMeldingService: PublishUbesvartMeldingService,
    override val intervalDelayMinutes: Long,
) : Cronjob {
    override val initialDelayMinutes: Long = 2

    override suspend fun run() {
        val result = runJob()
        log.info(
            "Completed publishing ubesvart melding processing job with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    suspend fun runJob(): CronjobResult {
        val result = CronjobResult()
        val unpublishedUbesvarteMeldinger = publishUbesvartMeldingService.getUnpublishedUbesvarteMeldinger()

        unpublishedUbesvarteMeldinger.forEach { meldingTilBehandler ->
            try {
                publishUbesvartMeldingService.publishUbesvartMelding(meldingTilBehandler)
                result.updated++
            } catch (e: Exception) {
                log.error("Caught exception in publish ubesvart melding", e)
                result.failed++
            }
        }

        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(UbesvartMeldingCronjob::class.java)
    }
}
