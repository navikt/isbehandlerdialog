package no.nav.syfo.infrastructure.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.infrastructure.kafka.producer.PublishAvvistMeldingService
import org.slf4j.LoggerFactory

class AvvistMeldingCronjob(
    val publishAvvistMeldingService: PublishAvvistMeldingService,
    override val intervalDelayMinutes: Long,
) : Cronjob {
    override val initialDelayMinutes: Long = 2

    override suspend fun run() {
        val result = runJob()
        log.info(
            "Completed publishing avvist melding status processing job with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    fun runJob(): CronjobResult {
        val result = CronjobResult()

        val unpublishedAvvisteMeldinger = publishAvvistMeldingService.getUnpublishedAvvisteMeldinger()

        unpublishedAvvisteMeldinger.forEach { avvistMeldingTilBehandler ->
            try {
                publishAvvistMeldingService.publishAvvistMelding(
                    avvistMeldingTilBehandler = avvistMeldingTilBehandler,
                )
                result.updated++
            } catch (e: Exception) {
                log.error("Caught exception in publish avvist melding", e)
                result.failed++
            }
        }

        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(AvvistMeldingCronjob::class.java)
    }
}
