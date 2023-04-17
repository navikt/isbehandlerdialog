package no.nav.syfo.melding.kafka

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.melding.database.domain.PMelding
import no.nav.syfo.melding.database.domain.toKafkaMeldingFraBehandlerDTO
import no.nav.syfo.melding.database.getUnpublishedMeldingerFraBehandler
import no.nav.syfo.melding.database.updateInnkommendePublishedAt
import java.util.*

class PublishMeldingFraBehandlerService(
    private val database: DatabaseInterface,
    private val kafkaMeldingFraBehandlerProducer: KafkaMeldingFraBehandlerProducer,
) {
    fun getUnpublishedMeldingerFraBehandler(): List<PMelding> {
        return database.getUnpublishedMeldingerFraBehandler()
    }

    fun publishMeldingFraBehandler(
        pMelding: PMelding,
    ) {
        kafkaMeldingFraBehandlerProducer.sendMeldingFraBehandler(
            kafkaMeldingFraBehandlerDTO = pMelding.toKafkaMeldingFraBehandlerDTO(),
            key = UUID.nameUUIDFromBytes(pMelding.arbeidstakerPersonIdent.toByteArray()),
        )

        database.updateInnkommendePublishedAt(
            uuid = pMelding.uuid,
        )
    }
}
