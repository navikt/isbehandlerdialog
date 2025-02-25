package no.nav.syfo.identhendelse

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.identhendelse.kafka.KafkaIdenthendelseDTO
import no.nav.syfo.melding.database.getMeldingerForArbeidstaker
import no.nav.syfo.melding.database.updateArbeidstakerPersonident
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class IdenthendelseService(
    private val database: DatabaseInterface,
) {

    private val log: Logger = LoggerFactory.getLogger(IdenthendelseService::class.java)

    fun handleIdenthendelse(identhendelse: KafkaIdenthendelseDTO) {
        if (identhendelse.folkeregisterIdenter.size > 1) {
            val activeIdent = identhendelse.getActivePersonident()
            if (activeIdent != null) {
                val inactiveIdenter = identhendelse.getInactivePersonidenter()
                val meldingerWithOldIdent = inactiveIdenter.flatMap { personident ->
                    database.getMeldingerForArbeidstaker(personident)
                }

                if (meldingerWithOldIdent.isNotEmpty()) {
                    database.updateArbeidstakerPersonident(meldingerWithOldIdent, activeIdent)
                    log.info("Identhendelse: Updated ${meldingerWithOldIdent.size} meldinger based on Identhendelse from PDL")
                }
            } else {
                log.warn("Mangler gyldig ident fra identhendelse-topic fra PDL")
            }
        }
    }
}
