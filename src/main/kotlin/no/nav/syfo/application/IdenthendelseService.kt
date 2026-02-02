package no.nav.syfo.application

import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.updateArbeidstakerPersonident
import no.nav.syfo.infrastructure.kafka.identhendelse.KafkaIdenthendelseDTO
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class IdenthendelseService(
    private val database: DatabaseInterface,
    private val meldingRepository: IMeldingRepository,
) {

    private val log: Logger = LoggerFactory.getLogger(IdenthendelseService::class.java)

    suspend fun handleIdenthendelse(identhendelse: KafkaIdenthendelseDTO) {
        if (identhendelse.folkeregisterIdenter.size > 1) {
            val activeIdent = identhendelse.getActivePersonident()
            if (activeIdent != null) {
                val inactiveIdenter = identhendelse.getInactivePersonidenter()
                val meldingerWithOldIdent = inactiveIdenter.flatMap { personident ->
                    meldingRepository.getMeldingerForArbeidstaker(personident)
                }

                if (meldingerWithOldIdent.isNotEmpty()) {
                    database.updateArbeidstakerPersonident(meldingerWithOldIdent, activeIdent)
                    log.info("Identhendelse: Updated ${meldingerWithOldIdent.size} meldinger based on Identhendelse from PDL")
                }
            }
        }
    }
}
