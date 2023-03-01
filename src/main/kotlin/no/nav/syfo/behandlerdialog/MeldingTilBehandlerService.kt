package no.nav.syfo.behandlerdialog

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.behandlerdialog.domain.MeldingTilBehandler
import no.nav.syfo.dialogmelding.database.createMeldingTilBehandler
import no.nav.syfo.dialogmelding.database.domain.toMeldingTilBehandler
import no.nav.syfo.dialogmelding.database.getMeldingerForArbeidstaker
import no.nav.syfo.domain.PersonIdent

class MeldingTilBehandlerService(
    private val database: DatabaseInterface,
) {
    fun createMeldingTilBehandler(
        meldingTilBehandler: MeldingTilBehandler,
    ) {
        database.connection.use { connection ->
            connection.createMeldingTilBehandler(
                meldingTilBehandler = meldingTilBehandler,
            )
        }
        // TODO: legg til i joark-cronjob og send på kafka her
    }

    fun getMeldingerTilBehandler(personIdent: PersonIdent): List<MeldingTilBehandler> {
        // TODO: Her ville vi vel gruppere det på en eller annen måte for ulike samtaler/dialoger
        return database.getMeldingerForArbeidstaker(personIdent)
            .filter { !it.innkommende } // Foreløpig bare ta MeldingTilBehandler-meldingene
            .map { it.toMeldingTilBehandler() }
    }
}
