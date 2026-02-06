package no.nav.syfo.infrastructure.kafka.producer

import no.nav.syfo.application.IMeldingRepository
import no.nav.syfo.infrastructure.database.domain.toMeldingTilBehandler
import no.nav.syfo.domain.MeldingTilBehandler
import java.time.OffsetDateTime
import java.util.*

class PublishUbesvartMeldingService(
    private val meldingRepository: IMeldingRepository,
    private val ubesvartMeldingProducer: UbesvartMeldingProducer,
    private val fristHours: Long,
) {
    suspend fun getUnpublishedUbesvarteMeldinger(): List<MeldingTilBehandler> {
        val fristDato = OffsetDateTime.now().minusHours(fristHours)
        return meldingRepository.getUbesvarteMeldinger(fristDato = fristDato).map { it.toMeldingTilBehandler() }
    }

    suspend fun publishUbesvartMelding(
        meldingTilBehandler: MeldingTilBehandler,
    ) {
        ubesvartMeldingProducer.sendUbesvartMelding(
            meldingTilBehandler = meldingTilBehandler,
            key = UUID.nameUUIDFromBytes(meldingTilBehandler.arbeidstakerPersonIdent.value.toByteArray())
        )
        meldingRepository.updateUbesvartPublishedAt(uuid = meldingTilBehandler.uuid)
    }
}
