package no.nav.syfo.application

import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.domain.toMeldingTilBehandler
import no.nav.syfo.infrastructure.database.getIkkeJournalforteMeldingerTilBehandler
import no.nav.syfo.infrastructure.database.updateMeldingJournalpostId
import no.nav.syfo.domain.MeldingTilBehandler

class JournalforMeldingTilBehandlerService(
    private val database: DatabaseInterface,
) {

    fun getIkkeJournalforte(): List<Pair<MeldingTilBehandler, ByteArray>> {
        return database.getIkkeJournalforteMeldingerTilBehandler().map { (pMelding, pdf) ->
            Pair(pMelding.toMeldingTilBehandler(), pdf)
        }
    }

    fun updateJournalpostId(melding: MeldingTilBehandler, journalpostId: String) {
        database.updateMeldingJournalpostId(melding, journalpostId)
    }
}
