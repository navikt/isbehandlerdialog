package no.nav.syfo.melding.kafka.producer

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.melding.database.domain.toMeldingTilBehandler
import no.nav.syfo.melding.database.getMeldingerByIds
import no.nav.syfo.melding.domain.Melding
import no.nav.syfo.melding.status.database.getUnpublishedAvvistMeldingStatus
import no.nav.syfo.melding.status.database.updateAvvistMeldingPublishedAt

class PublishAvvistMeldingStatusService(
    private val database: DatabaseInterface,
) {

    fun getUnpublishedAvvisteMeldinger(): List<Melding> {
        val unpublishedAvvisteMeldingerIds = database.getUnpublishedAvvistMeldingStatus()
        return database.getMeldingerByIds(unpublishedAvvisteMeldingerIds)
            .map { it.toMeldingTilBehandler() }
    }

    fun publishAvvistMelding(melding: Melding) {
        // TODO:
        // - Publiser på kafka
        // - Oppdater avvist_published_at i MeldingStatus tabell
        database.updateAvvistMeldingPublishedAt(meldingId = melding.id)
    }
}
