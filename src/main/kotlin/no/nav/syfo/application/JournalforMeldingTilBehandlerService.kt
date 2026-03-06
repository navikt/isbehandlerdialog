package no.nav.syfo.application

import no.nav.syfo.domain.Melding

class JournalforMeldingTilBehandlerService(
    private val meldingRepository: IMeldingRepository,
) {

    fun getIkkeJournalforte(): List<Pair<Melding.MeldingTilBehandler, ByteArray>> =
        meldingRepository.getIkkeJournalforteMeldingerTilBehandler()

    fun updateJournalpostId(melding: Melding.MeldingTilBehandler, journalpostId: String) {
        meldingRepository.updateMeldingJournalpostId(melding, journalpostId)
    }
}
