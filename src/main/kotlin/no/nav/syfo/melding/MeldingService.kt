package no.nav.syfo.melding

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.api.*
import no.nav.syfo.melding.database.*
import no.nav.syfo.melding.database.domain.toMeldingFraBehandler
import no.nav.syfo.melding.database.domain.toMeldingTilBehandler
import no.nav.syfo.melding.domain.*
import no.nav.syfo.melding.kafka.DialogmeldingBestillingProducer
import java.util.*

class MeldingService(
    private val database: DatabaseInterface,
    private val dialogmeldingBestillingProducer: DialogmeldingBestillingProducer,
    private val pdfgenClient: PdfGenClient,
) {
    suspend fun createMeldingTilBehandler(
        callId: String,
        meldingTilBehandler: MeldingTilBehandler,
    ) {
        val pdf = pdfgenClient.generateForesporselOmPasient(
            callId = callId,
            documentComponentDTOList = meldingTilBehandler.document
        ) ?: throw RuntimeException("Failed to request PDF - Dialogmelding forespørsel om pasient")

        database.connection.use { connection ->
            val meldingId = connection.createMeldingTilBehandler(
                meldingTilBehandler = meldingTilBehandler,
                commit = false,
            )
            connection.createPdf(
                pdf = pdf,
                meldingId = meldingId,
                commit = false,
            )
            connection.commit()
        }

        dialogmeldingBestillingProducer.sendDialogmeldingBestilling(
            meldingTilBehandler = meldingTilBehandler,
            meldingPdf = pdf,
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

    fun getVedlegg(
        msgId: String,
        vedleggNumber: Int,
    ): PdfContent? =
        database.getVedlegg(
            msgId = msgId,
            number = vedleggNumber,
        )?.let {
            PdfContent(it.pdf)
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
