package no.nav.syfo.melding.status.database

import no.nav.syfo.melding.database.domain.PMelding
import no.nav.syfo.melding.status.domain.MeldingStatus
import no.nav.syfo.melding.status.domain.MeldingStatusType
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
    val avvistPublishedAt: OffsetDateTime,
)

fun PMeldingStatus.toMeldingStatus(): MeldingStatus = MeldingStatus(
    uuid = this.uuid,
    status = MeldingStatusType.valueOf(this.status),
    tekst = this.tekst,
)
