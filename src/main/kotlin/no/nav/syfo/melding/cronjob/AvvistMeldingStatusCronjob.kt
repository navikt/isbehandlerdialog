package no.nav.syfo.melding.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.cronjob.Cronjob
import no.nav.syfo.application.cronjob.CronjobResult
import no.nav.syfo.melding.kafka.producer.PublishAvvistMeldingStatusService
import org.slf4j.LoggerFactory

class AvvistMeldingStatusCronjob(
    val publishAvvistMeldingStatusService: PublishAvvistMeldingStatusService,
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

        // TODO: Implement logic
        // 1. Utvide eksisterende queries på melding_status med det nye feltet
        // 2. Hente ut unpublished avviste statuser fra db
        val unpublishedAvvisteMeldinger = publishAvvistMeldingStatusService.getUnpublishedAvvisteMeldinger()

        // 3. Loope gjennom og "publisere" og oppdatere avvist_published_at
        unpublishedAvvisteMeldinger.forEach { avvistMeldingTilBehandler ->
            try {
                publishAvvistMeldingStatusService.publishAvvistMelding(avvistMeldingTilBehandler)
                result.updated++
            } catch (e: Exception) {
                log.error("Caught exception in publish avvist melding", e)
                result.failed++
            }
        }

        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(AvvistMeldingStatusCronjob::class.java)
    }
}
