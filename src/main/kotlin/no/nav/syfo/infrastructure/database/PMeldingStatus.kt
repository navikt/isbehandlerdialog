package no.nav.syfo.infrastructure.database

import no.nav.syfo.infrastructure.database.domain.PMelding
import no.nav.syfo.domain.MeldingStatus
import no.nav.syfo.domain.MeldingStatusType
import java.time.OffsetDateTime
import java.util.*

data class PMeldingStatus(
    val id: Int,
    val meldingId: PMelding.Id,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val status: String,
    val tekst: String?,
)

fun PMeldingStatus.toMeldingStatus(): MeldingStatus = MeldingStatus(
    uuid = this.uuid,
    status = MeldingStatusType.valueOf(this.status),
    tekst = this.tekst,
)
