package no.nav.syfo.melding.kafka.producer

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.melding.database.domain.PMelding
import no.nav.syfo.melding.database.domain.toMeldingTilBehandler
import no.nav.syfo.melding.database.getMeldingerByIds
import no.nav.syfo.melding.domain.MeldingTilBehandler
import no.nav.syfo.melding.status.database.getUnpublishedAvvistMeldingStatus
import no.nav.syfo.melding.status.database.updateAvvistMeldingPublishedAt

class PublishAvvistMeldingStatusService(
    private val database: DatabaseInterface,
    private val avvistMeldingProducer: AvvistMeldingProducer
) {

    fun getUnpublishedAvvisteMeldinger(): List<Pair<PMelding.Id, MeldingTilBehandler>> {
        val unpublishedAvvisteMeldingerIds = database.getUnpublishedAvvistMeldingStatus()
        return database.getMeldingerByIds(unpublishedAvvisteMeldingerIds)
            .map { Pair(it.id, it.toMeldingTilBehandler()) }
    }

    fun publishAvvistMelding(id: PMelding.Id, melding: MeldingTilBehandler) {
        avvistMeldingProducer.sendAvvistMelding(melding)
        database.updateAvvistMeldingPublishedAt(meldingId = id)
    }
}
