package no.nav.syfo.application

import no.nav.syfo.domain.Melding
import no.nav.syfo.domain.VedleggPdf
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.database.domain.PMelding
import java.sql.Connection
import java.time.OffsetDateTime
import java.util.*

interface IMeldingRepository {
    suspend fun getMelding(uuid: UUID): PMelding?
    fun createMeldingTilBehandler(meldingTilBehandler: Melding.MeldingTilBehandler, pdf: ByteArray): Melding.MeldingTilBehandler

    fun createMeldingFraBehandler(
        meldingFraBehandler: Melding.MeldingFraBehandler,
        fellesformat: String? = null,
        connection: Connection? = null,
    ): PMelding

    fun getMeldingerForArbeidstaker(arbeidstakerPersonIdent: PersonIdent): List<PMelding>
    suspend fun getUbesvarteMeldingerTilBehandler(fristDato: OffsetDateTime): List<Melding.MeldingTilBehandler>
    suspend fun updateUbesvartPublishedAt(uuid: UUID)
    fun updateInnkommendePublishedAt(uuid: UUID)
    fun getVedlegg(uuid: UUID, number: Int): VedleggPdf?
    fun createVedlegg(pdf: ByteArray, meldingId: PMelding.Id, number: Int, connection: Connection): Int
    fun getUnpublishedMeldingerFraBehandler(): List<Melding.MeldingFraBehandler>
    fun getUnpublishedAvvisteMeldinger(): List<Melding.MeldingTilBehandler>
    fun updateAvvistMeldingPublishedAt(uuid: UUID)
}
