package no.nav.syfo.application

import no.nav.syfo.domain.Melding
import no.nav.syfo.domain.MeldingStatus
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.VedleggPdf
import no.nav.syfo.infrastructure.database.domain.PMelding
import java.sql.Connection
import java.time.OffsetDateTime
import java.util.*

interface IMeldingRepository {
    suspend fun getMelding(uuid: UUID): PMelding?
    suspend fun getMeldingTilBehandler(uuid: UUID): Melding.MeldingTilBehandler?
    suspend fun getMeldingFraBehandler(uuid: UUID): Melding.MeldingFraBehandler?
    fun createMeldingTilBehandler(meldingTilBehandler: Melding.MeldingTilBehandler, pdf: ByteArray): Melding.MeldingTilBehandler

    fun createMeldingFraBehandler(
        meldingFraBehandler: Melding.MeldingFraBehandler,
        fellesformat: String? = null,
        connection: Connection? = null,
    ): PMelding

    fun getMeldingerForArbeidstaker(arbeidstakerPersonIdent: PersonIdent): List<PMelding>
    fun getMeldingStatus(meldingId: PMelding.Id, transaction: ITransaction? = null): MeldingStatus?
    suspend fun getUbesvarteMeldingerTilBehandler(fristDato: OffsetDateTime): List<Melding.MeldingTilBehandler>
    suspend fun updateUbesvartPublishedAt(uuid: UUID)
    fun updateInnkommendePublishedAt(uuid: UUID)
    fun getVedlegg(uuid: UUID, number: Int): VedleggPdf?
    fun createVedlegg(pdf: ByteArray, meldingId: PMelding.Id, number: Int, connection: Connection): Int
    fun getUnpublishedMeldingerFraBehandler(): List<Melding.MeldingFraBehandler>
    fun getUnpublishedAvvisteMeldinger(): List<Melding.MeldingTilBehandler>
    fun updateAvvistMeldingPublishedAt(uuid: UUID)
    fun getIkkeJournalforteMeldingerTilBehandler(): List<Pair<Melding.MeldingTilBehandler, ByteArray>>
    fun getUtgaendeMeldingerWithType(
        meldingType: Melding.MeldingType,
        arbeidstakerPersonIdent: String,
        connection: Connection,
    ): List<PMelding>

    fun updateMeldingJournalpostId(melding: Melding.MeldingTilBehandler, journalpostId: String)
    fun updateArbeidstakerPersonident(meldinger: List<PMelding>, personident: PersonIdent)
    fun hasMelding(msgId: String): Boolean
    fun getMeldingForMsgId(msgId: String, connection: Connection): PMelding?
    fun getUtgaendeMeldingerInConversation(uuidParam: UUID, arbeidstakerPersonIdent: PersonIdent): List<PMelding>
    fun getUtgaendeMeldingerInConversation(uuidParam: UUID, arbeidstakerPersonIdent: PersonIdent, connection: Connection): List<PMelding>
    fun getUtgaendeMeldingerInConversation(conversationRef: UUID, arbeidstakerPersonIdent: PersonIdent, type: Melding.MeldingType, connection: Connection): List<PMelding>
}
