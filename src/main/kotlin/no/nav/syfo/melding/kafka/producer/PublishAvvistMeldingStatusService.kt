package no.nav.syfo.melding.kafka.producer

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.melding.database.domain.PMelding
import no.nav.syfo.melding.database.domain.toMeldingTilBehandler
import no.nav.syfo.melding.database.getMeldingerByIds
import no.nav.syfo.melding.domain.Melding
import no.nav.syfo.melding.status.database.getUnpublishedAvvistMeldingStatus
import no.nav.syfo.melding.status.database.updateAvvistMeldingPublishedAt

class PublishAvvistMeldingStatusService(
    private val database: DatabaseInterface,
) {

    fun getUnpublishedAvvisteMeldinger(): List<Pair<PMelding.Id, Melding>> {
        val unpublishedAvvisteMeldingerIds = database.getUnpublishedAvvistMeldingStatus()
        return database.getMeldingerByIds(unpublishedAvvisteMeldingerIds)
            .map { Pair(it.id, it.toMeldingTilBehandler()) }
    }

    fun publishAvvistMelding(id: PMelding.Id, melding: Melding) {
        // TODO: Publiser på kafka

        database.updateAvvistMeldingPublishedAt(meldingId = id)
    }
}
