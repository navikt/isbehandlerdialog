package no.nav.syfo.melding

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.melding.database.domain.toMeldingTilBehandler
import no.nav.syfo.melding.database.getIkkeJournalforteMeldingerTilBehandler
import no.nav.syfo.melding.database.updateMeldingJournalpostId
import no.nav.syfo.melding.domain.MeldingTilBehandler
import no.nav.syfo.melding.domain.toPMelding

class JournalforMeldingTilBehandlerService(
    private val database: DatabaseInterface
) {

    fun getIkkeJournalforte(): List<Pair<MeldingTilBehandler, ByteArray>> {
        return database.getIkkeJournalforteMeldingerTilBehandler().map { (pMelding, pdf) ->
            Pair(pMelding.toMeldingTilBehandler(), pdf)
        }
    }

    fun updateJournalpostId(melding: MeldingTilBehandler, journalpostId: String) {
        database.updateMeldingJournalpostId(melding.toPMelding(), journalpostId)
    }
}
