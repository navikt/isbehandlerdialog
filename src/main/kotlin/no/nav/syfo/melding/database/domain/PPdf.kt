package no.nav.syfo.melding.database.domain

import java.time.OffsetDateTime
import java.util.*

data class PPdf(
    val meldingId: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val pdf: ByteArray,
)
