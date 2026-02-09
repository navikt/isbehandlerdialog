package no.nav.syfo.infrastructure.kafka.producer

import no.nav.syfo.application.IMeldingRepository
import no.nav.syfo.domain.MeldingFraBehandler
import no.nav.syfo.infrastructure.kafka.domain.KafkaMeldingDTO
import java.util.*

class PublishMeldingFraBehandlerService(
    private val meldingRepository: IMeldingRepository,
    private val meldingFraBehandlerProducer: MeldingFraBehandlerProducer,
) {
    fun getUnpublishedMeldingerFraBehandler(): List<MeldingFraBehandler> =
        meldingRepository.getUnpublishedMeldingerFraBehandler()

    fun publishMeldingFraBehandler(
        meldingFraBehandler: MeldingFraBehandler,
    ) {
        meldingFraBehandlerProducer.sendMeldingFraBehandler(
            kafkaMeldingDTO = KafkaMeldingDTO.from(meldingFraBehandler),
            key = UUID.nameUUIDFromBytes(meldingFraBehandler.arbeidstakerPersonIdent.value.toByteArray()),
        )

        meldingRepository.updateInnkommendePublishedAt(
            uuid = meldingFraBehandler.uuid,
        )
    }
}
