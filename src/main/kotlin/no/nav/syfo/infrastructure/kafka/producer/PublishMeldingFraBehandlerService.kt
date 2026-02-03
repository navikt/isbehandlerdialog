package no.nav.syfo.infrastructure.kafka.producer

import no.nav.syfo.application.IMeldingRepository
import no.nav.syfo.domain.MeldingFraBehandler
import no.nav.syfo.domain.toKafkaMeldingDTO
import java.util.*

class PublishMeldingFraBehandlerService(
    private val meldingRepository: IMeldingRepository,
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

        meldingRepository.updateInnkommendePublishedAt(
            uuid = meldingFraBehandler.uuid,
        )
    }
}
