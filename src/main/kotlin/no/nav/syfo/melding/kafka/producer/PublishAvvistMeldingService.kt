package no.nav.syfo.melding.kafka.producer

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.melding.database.domain.toMeldingTilBehandler
import no.nav.syfo.melding.database.getUnpublishedAvvisteMeldinger
import no.nav.syfo.melding.database.updateAvvistMeldingPublishedAt
import no.nav.syfo.melding.domain.Melding

class PublishAvvistMeldingService(
    private val database: DatabaseInterface,
) {

    fun getUnpublishedAvvisteMeldinger(): List<Melding> {
        return database.getUnpublishedAvvisteMeldinger().map { it.toMeldingTilBehandler() }
    }

    fun publishAvvistMelding(avvistMeldingTilBehandler: Melding) {
        // TODO: Publiser på kafka

        database.updateAvvistMeldingPublishedAt(avvistMeldingTilBehandler.uuid)
    }
}
