package no.nav.syfo.melding

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.melding.domain.MeldingTilBehandler
import no.nav.syfo.melding.database.createMeldingTilBehandler
import no.nav.syfo.melding.database.domain.toMeldingTilBehandler
import no.nav.syfo.melding.database.getMeldingerForArbeidstaker
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.kafka.DialogmeldingBestillingProducer

class MeldingService(
    private val database: DatabaseInterface,
    private val dialogmeldingBestillingProducer: DialogmeldingBestillingProducer,
) {
    fun createMeldingTilBehandler(
        meldingTilBehandler: MeldingTilBehandler,
    ) {
        database.connection.use { connection ->
            connection.createMeldingTilBehandler(
                meldingTilBehandler = meldingTilBehandler,
            )
        }
        dialogmeldingBestillingProducer.sendDialogmeldingBestilling(
            meldingTilBehandler = meldingTilBehandler,
        )

        // TODO: legg til i joark-cronjob her
    }

    fun getMeldingerTilBehandler(personIdent: PersonIdent): List<MeldingTilBehandler> {
        // TODO: Her ville vi vel gruppere det på en eller annen måte for ulike samtaler/dialoger
        return database.getMeldingerForArbeidstaker(personIdent)
            .filter { !it.innkommende } // Foreløpig bare ta MeldingTilBehandler-meldingene
            .map { it.toMeldingTilBehandler() }
    }
}
