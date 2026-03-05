package no.nav.syfo.infrastructure.kafka.producer

import no.nav.syfo.application.IMeldingRepository
import no.nav.syfo.domain.Melding

class PublishAvvistMeldingService(
    private val meldingRepository: IMeldingRepository,
    private val avvistMeldingProducer: AvvistMeldingProducer,
) {

    fun getUnpublishedAvvisteMeldinger(): List<Melding.MeldingTilBehandler> =
        meldingRepository.getUnpublishedAvvisteMeldinger()

    fun publishAvvistMelding(avvistMeldingTilBehandler: Melding.MeldingTilBehandler) {
        avvistMeldingProducer.sendAvvistMelding(avvistMeldingTilBehandler)
        meldingRepository.updateAvvistMeldingPublishedAt(uuid = avvistMeldingTilBehandler.uuid)
    }
}
