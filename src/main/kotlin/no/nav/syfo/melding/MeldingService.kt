package no.nav.syfo.melding

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.api.*
import no.nav.syfo.melding.database.*
import no.nav.syfo.melding.database.domain.*
import no.nav.syfo.melding.domain.*
import no.nav.syfo.melding.kafka.producer.DialogmeldingBestillingProducer
import no.nav.syfo.melding.status.database.getMeldingStatus
import no.nav.syfo.melding.status.database.toMeldingStatus
import no.nav.syfo.melding.status.domain.MeldingStatus
import java.sql.Connection
import java.util.*

class MeldingService(
    private val database: DatabaseInterface,
    private val dialogmeldingBestillingProducer: DialogmeldingBestillingProducer,
    private val pdfgenClient: PdfGenClient,
) {
    suspend fun createMeldingTilBehandler(
        callId: String,
        requestDTO: MeldingTilBehandlerRequestDTO,
        veilederIdent: String,
        personIdent: PersonIdent,
    ) {
        val meldingTilBehandler = MeldingTilBehandler.createMeldingTilBehandler(
            type = requestDTO.type,
            behandlerIdent = requestDTO.behandlerIdent,
            behandlerNavn = requestDTO.behandlerNavn,
            behandlerRef = requestDTO.behandlerRef,
            tekst = requestDTO.tekst,
            document = requestDTO.document,
            personIdent = personIdent,
            veilederIdent = veilederIdent,
        )
        val pdf = createPdf(callId, meldingTilBehandler)
        createMeldingTilBehandlerAndSendDialogmeldingBestilling(meldingTilBehandler = meldingTilBehandler, pdf = pdf)
    }

    fun getConversations(personIdent: PersonIdent): Map<UUID, List<MeldingDTO>> {
        val meldinger = database.getMeldingerForArbeidstaker(personIdent)
        return meldinger.groupBy(
            keySelector = { it.conversationRef },
            valueTransform = {
                if (it.innkommende) {
                    val behandlerRef = getBehandlerRefForConversation(it.conversationRef, personIdent)
                    it.toMeldingFraBehandler().toMeldingDTO(behandlerRef)
                } else {
                    val meldingStatus = getMeldingStatus(meldingId = it.id)
                    it.toMeldingTilBehandler().toMeldingDTO(meldingStatus)
                }
            }
        )
    }

    fun getVedlegg(
        uuid: UUID,
        vedleggNumber: Int,
    ): PdfContent? =
        database.getVedlegg(
            uuid = uuid,
            number = vedleggNumber,
        )?.let {
            PdfContent(it.pdf)
        }

    private fun getBehandlerRefForConversation(conversationRef: UUID, personIdent: PersonIdent): UUID {
        return getUtgaendeMeldingerInConversation(
            conversationRef = conversationRef,
            personIdent = personIdent,
        )
            .firstOrNull()
            ?.behandlerRef
            ?: throw IllegalStateException("Fant ikke behandlerRef for samtale $conversationRef, kunne ikke knyttes til melding fra behandler")
    }

    fun getArbeidstakerPersonIdentForMelding(meldingUuid: UUID): PersonIdent {
        val pMelding = database.getMelding(meldingUuid) ?: throw IllegalArgumentException("Melding not found")
        return PersonIdent(pMelding.arbeidstakerPersonIdent)
    }

    internal fun getMeldingStatus(
        meldingId: PMelding.Id,
        connection: Connection? = null,
    ): MeldingStatus? = database.getMeldingStatus(meldingId = meldingId, connection = connection)?.toMeldingStatus()

    private fun getMeldingTilBehandler(meldingUuid: UUID): MeldingTilBehandler? {
        return database.getMelding(meldingUuid)?.takeUnless { it.innkommende }?.toMeldingTilBehandler()
    }

    private fun getMeldingFraBehandler(meldingUuid: UUID): MeldingFraBehandler? {
        return database.getMelding(meldingUuid)?.takeUnless { !it.innkommende }?.toMeldingFraBehandler()
    }

    private fun getUtgaendeMeldingerInConversation(conversationRef: UUID, personIdent: PersonIdent): List<MeldingTilBehandler> {
        return database.connection.use {
            it.getUtgaendeMeldingerInConversation(
                conversationRef = conversationRef,
                arbeidstakerPersonIdent = personIdent,
            )
        }.map { it.toMeldingTilBehandler() }
    }

    internal suspend fun createPaminnelse(
        callId: String,
        meldingUuid: UUID,
        veilederIdent: String,
        document: List<DocumentComponentDTO>
    ) {
        val opprinneligMelding = getMeldingTilBehandler(meldingUuid = meldingUuid)
            ?: throw IllegalArgumentException("Failed to create påminnelse: Melding with uuid $meldingUuid does not exist")
        val paminnelse = MeldingTilBehandler.createForesporselPasientPaminnelse(
            opprinneligMelding = opprinneligMelding,
            veilederIdent = veilederIdent,
            document = document
        )

        val pdf = createPdf(callId = callId, meldingTilBehandler = paminnelse)
        createMeldingTilBehandlerAndSendDialogmeldingBestilling(meldingTilBehandler = paminnelse, pdf = pdf)
    }

    internal suspend fun createReturAvLegeerklaring(
        callId: String,
        meldingUuid: UUID,
        veilederIdent: String,
        document: List<DocumentComponentDTO>,
        tekst: String,
    ) {
        val innkommendeLegeerklaring = getMeldingFraBehandler(meldingUuid)
            ?.takeIf { it.type == MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING }
            ?: throw IllegalArgumentException("Failed to create retur av legeerklæring: Melding with uuid $meldingUuid does not exist")
        val opprinneligForesporselLegeerklaring = getUtgaendeMeldingerInConversation(
            conversationRef = innkommendeLegeerklaring.conversationRef,
            personIdent = innkommendeLegeerklaring.arbeidstakerPersonIdent,
        ).first { it.type == MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING }

        val returAvLegeerklaring = MeldingTilBehandler.createReturAvLegeerklaring(
            opprinneligForesporselLegeerklaring = opprinneligForesporselLegeerklaring,
            innkommendeLegeerklaring = innkommendeLegeerklaring,
            veilederIdent = veilederIdent,
            document = document,
            tekst = tekst,
        )

        val pdf = createPdf(callId = callId, meldingTilBehandler = returAvLegeerklaring)
        createMeldingTilBehandlerAndSendDialogmeldingBestilling(
            meldingTilBehandler = returAvLegeerklaring,
            pdf = pdf,
        )
    }

    private suspend fun createPdf(
        callId: String,
        meldingTilBehandler: MeldingTilBehandler
    ): ByteArray {
        return when (meldingTilBehandler.type) {
            MeldingType.FORESPORSEL_PASIENT_TILLEGGSOPPLYSNINGER -> pdfgenClient.generateForesporselOmPasientTilleggsopplysinger(
                callId = callId,
                documentComponentDTOList = meldingTilBehandler.document
            )

            MeldingType.FORESPORSEL_PASIENT_PAMINNELSE -> pdfgenClient.generateForesporselOmPasientPaminnelse(
                callId = callId,
                documentComponentDTOList = meldingTilBehandler.document
            )

            MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING -> pdfgenClient.generateForesporselOmPasientLegeerklaring(
                callId = callId,
                documentComponentDTOList = meldingTilBehandler.document
            )

            MeldingType.HENVENDELSE_RETUR_LEGEERKLARING -> pdfgenClient.generateReturAvLegeerklaring(
                callId = callId,
                documentComponentDTOList = meldingTilBehandler.document,
            )

            MeldingType.HENVENDELSE_MELDING_FRA_NAV -> pdfgenClient.generateMeldingFraNav(
                callId = callId,
                documentComponentDTOList = meldingTilBehandler.document,
            )
        } ?: throw RuntimeException("Failed to request PDF - ${meldingTilBehandler.type}")
    }

    private fun createMeldingTilBehandlerAndSendDialogmeldingBestilling(
        meldingTilBehandler: MeldingTilBehandler,
        pdf: ByteArray,
    ) {
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
    }
}
