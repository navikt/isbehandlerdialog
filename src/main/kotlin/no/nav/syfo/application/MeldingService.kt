package no.nav.syfo.application

import kotlinx.coroutines.runBlocking
import no.nav.syfo.api.models.MeldingDTO
import no.nav.syfo.api.models.MeldingTilBehandlerRequestDTO
import no.nav.syfo.domain.DocumentComponentDTO
import no.nav.syfo.domain.Melding
import no.nav.syfo.domain.MeldingStatus
import no.nav.syfo.domain.PdfContent
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.createMeldingFraBehandler
import no.nav.syfo.infrastructure.database.createMeldingTilBehandler
import no.nav.syfo.infrastructure.database.createPdf
import no.nav.syfo.infrastructure.database.domain.PMelding
import no.nav.syfo.infrastructure.database.domain.toMeldingFraBehandler
import no.nav.syfo.infrastructure.database.domain.toMeldingTilBehandler
import no.nav.syfo.infrastructure.database.getMeldingForMsgId
import no.nav.syfo.infrastructure.database.getMeldingStatus
import no.nav.syfo.infrastructure.database.getUtgaendeMeldingerInConversation
import no.nav.syfo.infrastructure.database.hasMelding
import no.nav.syfo.infrastructure.database.toMeldingStatus
import no.nav.syfo.infrastructure.kafka.dialogmelding.COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_MELDING_CREATED
import no.nav.syfo.infrastructure.kafka.dialogmelding.COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_DUPLICATE
import no.nav.syfo.infrastructure.kafka.dialogmelding.COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_NOT_FOR_MODIA
import no.nav.syfo.infrastructure.kafka.domain.KafkaDialogmeldingFraBehandlerDTO
import no.nav.syfo.infrastructure.kafka.domain.isHenvendelseTilNAV
import no.nav.syfo.infrastructure.kafka.domain.toMeldingFraBehandler
import no.nav.syfo.infrastructure.kafka.producer.DialogmeldingBestillingProducer
import java.sql.Connection
import java.util.*

class MeldingService(
    private val database: DatabaseInterface,
    private val meldingRepository: IMeldingRepository,
    private val dialogmeldingBestillingProducer: DialogmeldingBestillingProducer,
    private val oppfolgingstilfelleClient: IOppfolgingstilfelleClient,
    private val pdfgenClient: IPdfGenClient,
    private val padm2Client: IPadm2Client,
) {
    suspend fun createMeldingTilBehandler(
        callId: String,
        requestDTO: MeldingTilBehandlerRequestDTO,
        veilederIdent: String,
        personIdent: PersonIdent,
    ) {
        val meldingTilBehandler = Melding.MeldingTilBehandler.createMeldingTilBehandler(
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
                    MeldingDTO.from(meldingFraBehandler, behandlerRef)
                } else {
                    val meldingStatus = getMeldingStatus(meldingId = it.id)
                    MeldingDTO.from(it.toMeldingTilBehandler(), meldingStatus)
                }
            }
        )
    }

    private fun getBehandlerRefForConversation(
        meldingFraBehandler: Melding.MeldingFraBehandler,
        personIdent: PersonIdent,
    ): UUID? {
        val behandlerRef = getUtgaendeMeldingerInConversation(
            conversationRef = meldingFraBehandler.conversationRef,
            personIdent = personIdent,
        ).firstOrNull()?.behandlerRef
        if (meldingFraBehandler.type != Melding.MeldingType.HENVENDELSE_MELDING_TIL_NAV && behandlerRef == null) {
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

    fun receiveDialogmeldingFromBehandler(
        kafkaDialogmeldingFraBehandler: KafkaDialogmeldingFraBehandlerDTO,
        connection: Connection,
    ) {
        val conversationRef = kafkaDialogmeldingFraBehandler.conversationRef
        val conversationRefUuid = if (conversationRef.isNullOrBlank()) {
            null
        } else {
            try {
                UUID.fromString(conversationRef)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        val utgaendeMelding = findUtgaendeMelding(
            meldingParentRef = kafkaDialogmeldingFraBehandler.parentRef,
            arbeidstakerPersonIdent = PersonIdent(kafkaDialogmeldingFraBehandler.personIdentPasient),
            conversationRef = conversationRefUuid,
            connection = connection,
        )
        if (utgaendeMelding != null) {
            val utgaendeMelding = kafkaDialogmeldingFraBehandler.toMeldingFraBehandler(
                type = Melding.MeldingType.valueOf(utgaendeMelding.type),
                conversationRef = utgaendeMelding.conversationRef,
            )
            storeDialogmeldingFromBehandler(
                connection = connection,
                meldingFraBehandler = utgaendeMelding,
                fellesformatXML = kafkaDialogmeldingFraBehandler.fellesformatXML,
            )
        } else if (kafkaDialogmeldingFraBehandler.isHenvendelseTilNAV()) {
            val meldingFraBehandler = kafkaDialogmeldingFraBehandler.toMeldingFraBehandler(
                type = Melding.MeldingType.HENVENDELSE_MELDING_TIL_NAV,
                conversationRef = conversationRefUuid ?: UUID.randomUUID(),
            )
            handleHenvendelseTilNAV(
                connection = connection,
                meldingFraBehandler = meldingFraBehandler,
                fellesformatXML = kafkaDialogmeldingFraBehandler.fellesformatXML,
            )
        } else {
            COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_NOT_FOR_MODIA.increment()
            log.info(
                """
                    Received dialogmelding from behandler, but skipped since not for Modia
                    msgId: ${kafkaDialogmeldingFraBehandler.msgId}
                    conversationRef: ${kafkaDialogmeldingFraBehandler.conversationRef}
                    msgType: ${kafkaDialogmeldingFraBehandler.msgType}
                """.trimIndent()
            )
        }
    }

    private fun findUtgaendeMelding(
        meldingParentRef: String?,
        arbeidstakerPersonIdent: PersonIdent,
        conversationRef: UUID?,
        connection: Connection,
    ): PMelding? {
        val utgaaende = conversationRef?.let {
            connection.getUtgaendeMeldingerInConversation(
                uuidParam = conversationRef,
                arbeidstakerPersonIdent = arbeidstakerPersonIdent,
            )
        } ?: mutableListOf()

        if (utgaaende.isEmpty() && !meldingParentRef.isNullOrBlank()) {
            val parentRef = try {
                UUID.fromString(meldingParentRef)
            } catch (e: IllegalArgumentException) {
                null
            }
            if (parentRef != null) {
                utgaaende.addAll(
                    connection.getUtgaendeMeldingerInConversation(
                        uuidParam = parentRef,
                        arbeidstakerPersonIdent = arbeidstakerPersonIdent,
                    )
                )
            }
        }
        return utgaaende.firstOrNull()
    }

    private fun handleHenvendelseTilNAV(
        connection: Connection,
        meldingFraBehandler: Melding.MeldingFraBehandler,
        fellesformatXML: String,
    ) {
        val latestOppfolgingstilfelle = runBlocking {
            oppfolgingstilfelleClient.getOppfolgingstilfelle(personIdent = meldingFraBehandler.arbeidstakerPersonIdent)
        }
        if (latestOppfolgingstilfelle?.isActive() == true) {
            storeDialogmeldingFromBehandler(
                connection = connection,
                meldingFraBehandler = meldingFraBehandler,
                fellesformatXML = fellesformatXML,
            )
        } else {
            log.info("Received dialogmelding til NAV from behandler, but skipped since no active oppfolgingstilfelle")
            COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_NOT_FOR_MODIA.increment()
        }
    }

    private fun storeDialogmeldingFromBehandler(
        connection: Connection,
        meldingFraBehandler: Melding.MeldingFraBehandler,
        fellesformatXML: String,
    ) {
        val melding = connection.getMeldingForMsgId(meldingFraBehandler.msgId)
        val isDuplicate = melding != null
        if (isDuplicate) {
            log.warn("Received a duplicate dialogmelding of type ${meldingFraBehandler.type} from behandler: ${meldingFraBehandler.conversationRef}")
            COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_SKIPPED_DUPLICATE.increment()
        } else {
            log.info("Received a dialogmelding of type ${meldingFraBehandler.type} from behandler: ${meldingFraBehandler.conversationRef}")

            val meldingId = connection.createMeldingFraBehandler(
                meldingFraBehandler = meldingFraBehandler,
                fellesformat = fellesformatXML,
            )
            if (meldingFraBehandler.antallVedlegg > 0) {
                lagreMeldingVedleggFraMelding(meldingId = meldingId, meldingFraBehandler = meldingFraBehandler, connection = connection)
            }
            COUNT_KAFKA_CONSUMER_DIALOGMELDING_FRA_BEHANDLER_MELDING_CREATED.increment()
        }
    }

    fun getVedlegg(uuid: UUID, vedleggNumber: Int): PdfContent? =
        meldingRepository.getVedlegg(
            uuid = uuid,
            number = vedleggNumber,
        )?.let {
            PdfContent(it.pdf)
        }

    fun lagreMeldingVedleggFraMelding(meldingId: PMelding.Id, meldingFraBehandler: Melding.MeldingFraBehandler, connection: Connection) {
        val vedlegg = runBlocking { padm2Client.hentVedlegg(meldingFraBehandler.msgId) }
        lagreVedlegg(vedlegg = vedlegg.map { it.bytes }, meldingId = meldingId, connection = connection)
    }

    fun lagreVedlegg(vedlegg: List<ByteArray>, meldingId: PMelding.Id, connection: Connection) {
        vedlegg.forEachIndexed { index, pdf ->
            meldingRepository.createVedlegg(
                pdf = pdf,
                meldingId = meldingId,
                number = index,
                connection = connection,
            )
        }
    }

    private suspend fun getMeldingTilBehandler(meldingUuid: UUID): Melding.MeldingTilBehandler? {
        return meldingRepository.getMelding(meldingUuid)?.takeUnless { it.innkommende }?.toMeldingTilBehandler()
    }

    private suspend fun getMeldingFraBehandler(meldingUuid: UUID): Melding.MeldingFraBehandler? {
        return meldingRepository.getMelding(meldingUuid)?.takeUnless { !it.innkommende }?.toMeldingFraBehandler()
    }

    private fun getUtgaendeMeldingerInConversation(
        conversationRef: UUID,
        personIdent: PersonIdent,
    ): List<Melding.MeldingTilBehandler> {
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
        val paminnelse = Melding.MeldingTilBehandler.createForesporselPasientPaminnelse(
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
            ?.takeIf { it.type == Melding.MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING }
            ?: throw IllegalArgumentException("Failed to create retur av legeerklæring: Melding with uuid $meldingUuid does not exist")
        val opprinneligForesporselLegeerklaring = getUtgaendeMeldingerInConversation(
            conversationRef = innkommendeLegeerklaring.conversationRef,
            personIdent = innkommendeLegeerklaring.arbeidstakerPersonIdent
        ).first { it.type == Melding.MeldingType.FORESPORSEL_PASIENT_LEGEERKLARING }

        val returAvLegeerklaring = Melding.MeldingTilBehandler.createReturAvLegeerklaring(
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
        meldingTilBehandler: Melding.MeldingTilBehandler,
    ): ByteArray =
        pdfgenClient.generateDialogPdf(
            callId = callId,
            mottakerNavn = meldingTilBehandler.behandlerNavn ?: "",
            documentComponentDTOList = meldingTilBehandler.document,
            meldingType = meldingTilBehandler.type,
        ) ?: throw RuntimeException("Failed to request PDF - ${meldingTilBehandler.type}")

    private fun createMeldingTilBehandlerAndSendDialogmeldingBestilling(
        meldingTilBehandler: Melding.MeldingTilBehandler,
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

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(MeldingService::class.java)
    }
}
