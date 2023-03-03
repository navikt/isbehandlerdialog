package no.nav.syfo.melding

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.melding.domain.MeldingTilBehandler
import no.nav.syfo.melding.database.createMeldingTilBehandler
import no.nav.syfo.melding.database.domain.toMeldingTilBehandler
import no.nav.syfo.melding.database.getMeldingerForArbeidstaker
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.api.Melding
import no.nav.syfo.melding.database.domain.toMeldingFraBehandler
import no.nav.syfo.melding.database.getMeldingerForConversation
import no.nav.syfo.melding.domain.toMelding
import java.util.*

class MeldingService(
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

    fun getConversationMeldingerMap(personIdent: PersonIdent): Map<UUID, List<Melding>> {
        val meldinger = database.getMeldingerForArbeidstaker(personIdent)
        return meldinger.groupBy(
            keySelector = { it.conversationRef },
            valueTransform = {
                if (it.innkommende) {
                    val behandlerRef: UUID = database.getMeldingerForConversation(it.conversationRef)
                        .sortedBy { melding -> melding.tidspunkt } // TODO: Kan bruke parent her også, men foreløpig er den hardkodet til null for alle
                        .firstOrNull { melding -> !melding.innkommende }
                        ?.behandlerRef
                        ?: throw IllegalStateException("Melding fra behandler mangler behandlerRef")
                    it.toMeldingFraBehandler().toMelding(behandlerRef)
                } else {
                    it.toMeldingTilBehandler().toMelding()
                }
            }
        )
    }
}
