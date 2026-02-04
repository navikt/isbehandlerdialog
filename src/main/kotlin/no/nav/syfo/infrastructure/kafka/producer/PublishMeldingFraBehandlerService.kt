package no.nav.syfo.infrastructure.kafka.producer

import no.nav.syfo.application.IMeldingRepository
import no.nav.syfo.domain.MeldingFraBehandler
import no.nav.syfo.domain.toKafkaMeldingDTO
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.updateInnkommendePublishedAt
import java.util.*

class PublishMeldingFraBehandlerService(
    private val meldingRepository: IMeldingRepository,
    private val database: DatabaseInterface,
    private val kafkaMeldingFraBehandlerProducer: KafkaMeldingFraBehandlerProducer,
) {
    fun getUnpublishedMeldingerFraBehandler(): List<MeldingFraBehandler> =
        meldingRepository.getUnpublishedMeldingerFraBehandler()

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
