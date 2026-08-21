package no.nav.syfo.application

import no.nav.syfo.infrastructure.kafka.janitor.JanitorAction
import no.nav.syfo.infrastructure.kafka.janitor.JanitorEventDTO
import no.nav.syfo.infrastructure.kafka.janitor.JanitorEventStatus
import no.nav.syfo.infrastructure.kafka.janitor.JanitorEventStatusDTO
import no.nav.syfo.infrastructure.kafka.janitor.JanitorEventStatusProducer
import org.slf4j.LoggerFactory
import java.util.UUID

class JanitorService(
    private val meldingRepository: IMeldingRepository,
    private val janitorEventStatusProducer: JanitorEventStatusProducer,
) {
    suspend fun handle(event: JanitorEventDTO) {
        log.info("Received janitor event ${event.action} with reference ${event.referenceUUID}")
        when (event.action) {
            JanitorAction.SLETT_BEHANDLERDIALOG.name -> {
                val result = handleSlett(event)
                result.onFailure { log.error("Failed to handle janitor event ${event.action}", it) }
                sendEventStatus(event, result)
            }
            else -> log.trace("Irrelevant janitor event: ${event.action}")
        }
    }

    private suspend fun handleSlett(event: JanitorEventDTO): Result<Unit> = runCatching {
        val meldingUUID = UUID.fromString(event.referenceUUID)
        val melding = meldingRepository.getMelding(meldingUUID)
            ?: throw RuntimeException("Melding med uuid $meldingUUID ikke funnet")

        if (melding.arbeidstakerPersonIdent != event.personident) {
            throw RuntimeException("Melding gjelder ikke oppgitt person")
        }

        log.info("Sletter innhold i melding med uuid $meldingUUID")
        meldingRepository.slettMelding(meldingUUID)
    }

    private fun sendEventStatus(event: JanitorEventDTO, result: Result<Unit>) {
        janitorEventStatusProducer.sendEventStatus(
            JanitorEventStatusDTO(
                eventUUID = event.eventUUID,
                status = if (result.isSuccess) JanitorEventStatus.OK else JanitorEventStatus.FAILED,
            )
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(JanitorService::class.java)
    }
}
