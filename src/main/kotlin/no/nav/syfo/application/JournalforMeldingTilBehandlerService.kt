package no.nav.syfo.application

import no.nav.syfo.domain.Melding
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.domain.toMeldingTilBehandler
import no.nav.syfo.infrastructure.database.getIkkeJournalforteMeldingerTilBehandler
import no.nav.syfo.infrastructure.database.updateMeldingJournalpostId

class JournalforMeldingTilBehandlerService(
    private val database: DatabaseInterface,
) {

    fun getIkkeJournalforte(): List<Pair<Melding.MeldingTilBehandler, ByteArray>> {
        return database.getIkkeJournalforteMeldingerTilBehandler().map { (pMelding, pdf) ->
            Pair(pMelding.toMeldingTilBehandler(), pdf)
        }
    }

    fun updateJournalpostId(melding: Melding.MeldingTilBehandler, journalpostId: String) {
        database.updateMeldingJournalpostId(melding, journalpostId)
    }
}
