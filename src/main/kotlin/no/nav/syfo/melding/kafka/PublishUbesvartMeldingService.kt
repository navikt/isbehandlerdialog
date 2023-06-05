package no.nav.syfo.melding.kafka

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.melding.database.domain.toMeldingTilBehandler
import no.nav.syfo.melding.database.getUbesvarteMeldinger
import no.nav.syfo.melding.database.updateUbesvartPublishedAt
import no.nav.syfo.melding.domain.MeldingTilBehandler
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
