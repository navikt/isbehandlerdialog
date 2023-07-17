package no.nav.syfo.melding.kafka.producer

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.melding.database.domain.toMeldingTilBehandler
import no.nav.syfo.melding.database.getUnpublishedAvvisteMeldinger
import no.nav.syfo.melding.database.updateAvvistMeldingPublishedAt
import no.nav.syfo.melding.domain.MeldingTilBehandler

class PublishAvvistMeldingStatusService(
    private val database: DatabaseInterface,
    private val avvistMeldingProducer: AvvistMeldingProducer
) {

    fun getUnpublishedAvvisteMeldinger(): List<MeldingTilBehandler> =
        database.getUnpublishedAvvisteMeldinger().map { it.toMeldingTilBehandler() }

    fun publishAvvistMelding(melding: MeldingTilBehandler) {
        avvistMeldingProducer.sendAvvistMelding(melding)
        database.updateAvvistMeldingPublishedAt(uuid = melding.uuid)
    }
}
