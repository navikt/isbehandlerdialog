package no.nav.syfo.infrastructure.kafka.producer

import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.domain.toMeldingTilBehandler
import no.nav.syfo.infrastructure.database.getUbesvarteMeldinger
import no.nav.syfo.infrastructure.database.updateUbesvartPublishedAt
import no.nav.syfo.domain.MeldingTilBehandler
import java.time.OffsetDateTime
import java.util.*

class PublishUbesvartMeldingService(
    private val database: DatabaseInterface,
    private val kafkaUbesvartMeldingProducer: KafkaUbesvartMeldingProducer,
    private val fristHours: Long,
) {
    fun getUnpublishedUbesvarteMeldinger(): List<MeldingTilBehandler> {
        val fristDato = OffsetDateTime.now().minusHours(fristHours)
        return database.getUbesvarteMeldinger(fristDato = fristDato).map { it.toMeldingTilBehandler() }
    }

    fun publishUbesvartMelding(
        meldingTilBehandler: MeldingTilBehandler,
    ) {
        kafkaUbesvartMeldingProducer.sendUbesvartMelding(
            meldingTilBehandler = meldingTilBehandler,
            key = UUID.nameUUIDFromBytes(meldingTilBehandler.arbeidstakerPersonIdent.value.toByteArray())
        )
        database.updateUbesvartPublishedAt(uuid = meldingTilBehandler.uuid)
    }
}
