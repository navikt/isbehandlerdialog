package no.nav.syfo.melding.database.domain

import java.time.OffsetDateTime
import java.util.*

data class PVedlegg(
    val id: Int,
    val melding_id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val number: Int,
    val pdf: ByteArray,
)
