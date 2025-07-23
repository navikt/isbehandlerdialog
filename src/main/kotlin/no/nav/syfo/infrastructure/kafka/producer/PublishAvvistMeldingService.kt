package no.nav.syfo.infrastructure.kafka.producer

import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.domain.toMeldingTilBehandler
import no.nav.syfo.infrastructure.database.getUnpublishedAvvisteMeldinger
import no.nav.syfo.infrastructure.database.updateAvvistMeldingPublishedAt
import no.nav.syfo.domain.MeldingTilBehandler

class PublishAvvistMeldingService(
    private val database: DatabaseInterface,
    private val avvistMeldingProducer: AvvistMeldingProducer,
) {

    fun getUnpublishedAvvisteMeldinger(): List<MeldingTilBehandler> =
        database.getUnpublishedAvvisteMeldinger().map { it.toMeldingTilBehandler() }

    fun publishAvvistMelding(avvistMeldingTilBehandler: MeldingTilBehandler) {
        avvistMeldingProducer.sendAvvistMelding(avvistMeldingTilBehandler)
        database.updateAvvistMeldingPublishedAt(uuid = avvistMeldingTilBehandler.uuid)
    }
}
