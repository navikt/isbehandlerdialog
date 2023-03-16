package no.nav.syfo.melding

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.melding.domain.MeldingTilBehandler
import no.nav.syfo.melding.database.createMeldingTilBehandler
import no.nav.syfo.melding.database.domain.toMeldingTilBehandler
import no.nav.syfo.melding.database.getMeldingerForArbeidstaker
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.kafka.DialogmeldingBestillingProducer
import no.nav.syfo.melding.api.Melding
import no.nav.syfo.melding.database.domain.toMeldingFraBehandler
import no.nav.syfo.melding.database.getUtgaendeMeldingerInConversation
import no.nav.syfo.melding.domain.toMelding
import java.util.*

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

    fun getConversations(personIdent: PersonIdent): Map<UUID, List<Melding>> {
        val meldinger = database.getMeldingerForArbeidstaker(personIdent)
        return meldinger.groupBy(
            keySelector = { it.conversationRef },
            valueTransform = {
                if (it.innkommende) {
                    val behandlerRef = getBehandlerRefForConversation(it.conversationRef, personIdent)
                    it.toMeldingFraBehandler().toMelding(behandlerRef)
                } else {
                    it.toMeldingTilBehandler().toMelding()
                }
            }
        )
    }

    private fun getBehandlerRefForConversation(conversationRef: UUID, personIdent: PersonIdent): UUID {
        return database.connection.use {
            it.getUtgaendeMeldingerInConversation(conversationRef, personIdent)
        }
            .firstOrNull()
            ?.behandlerRef
            ?: throw IllegalStateException("Fant ikke behandlerRef for samtale $conversationRef, kunne ikke knyttes til melding fra behandler")
    }
}
