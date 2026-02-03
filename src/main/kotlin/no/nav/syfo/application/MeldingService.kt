package no.nav.syfo.application

import no.nav.syfo.api.models.MeldingDTO
import no.nav.syfo.api.models.MeldingTilBehandlerRequestDTO
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.client.pdfgen.PdfGenClient
import no.nav.syfo.infrastructure.database.*
import no.nav.syfo.infrastructure.database.domain.PMelding
import no.nav.syfo.infrastructure.database.domain.toMeldingFraBehandler
import no.nav.syfo.infrastructure.database.domain.toMeldingTilBehandler
import no.nav.syfo.infrastructure.kafka.producer.DialogmeldingBestillingProducer
import java.sql.Connection
import java.util.*

class MeldingService(
    private val database: DatabaseInterface,
    private val meldingRepository: IMeldingRepository,
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
        val meldinger = meldingRepository.getMeldingerForArbeidstaker(personIdent)
        return meldinger.groupBy(
            keySelector = { it.conversationRef },
            valueTransform = {
                if (it.innkommende) {
                    val meldingFraBehandler = it.toMeldingFraBehandler()
                    val behandlerRef = getBehandlerRefForConversation(
                        meldingFraBehandler = meldingFraBehandler,
                        personIdent = personIdent,
                    )
                    meldingFraBehandler.toMeldingDTO(behandlerRef)
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
        meldingRepository.getVedlegg(
            uuid = uuid,
            number = vedleggNumber,
        )?.let {
            PdfContent(it.pdf)
        }

    private fun getBehandlerRefForConversation(
        meldingFraBehandler: MeldingFraBehandler,
        personIdent: PersonIdent,
    ): UUID? {
        val behandlerRef = getUtgaendeMeldingerInConversation(
            conversationRef = meldingFraBehandler.conversationRef,
            personIdent = personIdent,
        ).firstOrNull()?.behandlerRef
        if (meldingFraBehandler.type != MeldingType.HENVENDELSE_MELDING_TIL_NAV && behandlerRef == null) {
            throw IllegalStateException("Fant ikke behandlerRef for samtale ${meldingFraBehandler.conversationRef}, kunne ikke knyttes til melding fra behandler")
        }
        return behandlerRef
    }

    suspend fun getArbeidstakerPersonIdentForMelding(meldingUuid: UUID): PersonIdent {
        val pMelding = meldingRepository.getMelding(meldingUuid) ?: throw IllegalArgumentException("Melding not found")
        return PersonIdent(pMelding.arbeidstakerPersonIdent)
    }

    internal fun getMeldingStatus(
        meldingId: PMelding.Id,
        connection: Connection? = null,
    ): MeldingStatus? = database.getMeldingStatus(meldingId = meldingId, connection = connection)?.toMeldingStatus()

    internal fun hasMelding(msgId: String): Boolean = database.hasMelding(msgId = msgId)

    private suspend fun getMeldingTilBehandler(meldingUuid: UUID): MeldingTilBehandler? {
        return meldingRepository.getMelding(meldingUuid)?.takeUnless { it.innkommende }?.toMeldingTilBehandler()
    }

    private suspend fun getMeldingFraBehandler(meldingUuid: UUID): MeldingFraBehandler? {
        return meldingRepository.getMelding(meldingUuid)?.takeUnless { !it.innkommende }?.toMeldingFraBehandler()
    }

    private fun getUtgaendeMeldingerInConversation(
        conversationRef: UUID,
        personIdent: PersonIdent,
    ): List<MeldingTilBehandler> {
        return database.connection.use {
            it.getUtgaendeMeldingerInConversation(
                uuidParam = conversationRef,
                arbeidstakerPersonIdent = personIdent,
            )
        }.map { it.toMeldingTilBehandler() }
    }

    internal suspend fun createPaminnelse(
        callId: String,
        meldingUuid: UUID,
        veilederIdent: String,
        document: List<DocumentComponentDTO>,
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
            personIdent = innkommendeLegeerklaring.arbeidstakerPersonIdent
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
        meldingTilBehandler: MeldingTilBehandler,
    ): ByteArray =
        pdfgenClient.generateDialogPdf(
            callId = callId,
            mottakerNavn = meldingTilBehandler.behandlerNavn ?: "",
            documentComponentDTOList = meldingTilBehandler.document,
            meldingType = meldingTilBehandler.type,
        ) ?: throw RuntimeException("Failed to request PDF - ${meldingTilBehandler.type}")

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
