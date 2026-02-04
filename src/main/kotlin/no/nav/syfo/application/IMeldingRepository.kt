package no.nav.syfo.application

import no.nav.syfo.domain.MeldingFraBehandler
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.database.domain.PMelding
import no.nav.syfo.infrastructure.database.domain.PVedlegg
import java.time.OffsetDateTime
import java.util.UUID

interface IMeldingRepository {
    suspend fun getMelding(uuid: UUID): PMelding?
    fun getMeldingerForArbeidstaker(arbeidstakerPersonIdent: PersonIdent): List<PMelding>
    suspend fun getUbesvarteMeldinger(fristDato: OffsetDateTime): List<PMelding>
    suspend fun updateUbesvartPublishedAt(uuid: UUID)
    fun getVedlegg(uuid: UUID, number: Int): PVedlegg?
    fun getUnpublishedMeldingerFraBehandler(): List<MeldingFraBehandler>
}
