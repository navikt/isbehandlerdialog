package no.nav.syfo.infrastructure.database.domain

import no.nav.syfo.domain.VedleggPdf
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
) {
    fun toVedleggPdf() =
        VedleggPdf(pdf)
}
