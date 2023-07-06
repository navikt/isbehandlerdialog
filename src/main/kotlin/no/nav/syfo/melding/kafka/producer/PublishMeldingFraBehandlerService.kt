package no.nav.syfo.melding.kafka.producer

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.melding.database.domain.toMeldingFraBehandler
import no.nav.syfo.melding.database.getUnpublishedMeldingerFraBehandler
import no.nav.syfo.melding.database.updateInnkommendePublishedAt
import no.nav.syfo.melding.domain.MeldingFraBehandler
import no.nav.syfo.melding.domain.toKafkaMeldingDTO
import java.util.*

class PublishMeldingFraBehandlerService(
    private val database: DatabaseInterface,
    private val kafkaMeldingFraBehandlerProducer: KafkaMeldingFraBehandlerProducer,
) {
    fun getUnpublishedMeldingerFraBehandler(): List<MeldingFraBehandler> {
        return database.getUnpublishedMeldingerFraBehandler().map { it.toMeldingFraBehandler() }
    }

    fun publishMeldingFraBehandler(
        meldingFraBehandler: MeldingFraBehandler,
    ) {
        kafkaMeldingFraBehandlerProducer.sendMeldingFraBehandler(
            kafkaMeldingDTO = meldingFraBehandler.toKafkaMeldingDTO(),
            key = UUID.nameUUIDFromBytes(meldingFraBehandler.arbeidstakerPersonIdent.value.toByteArray()),
        )

        database.updateInnkommendePublishedAt(
            uuid = meldingFraBehandler.uuid,
        )
    }
}
