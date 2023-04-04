package no.nav.syfo.melding.database.domain

import java.time.OffsetDateTime
import java.util.*

data class PPdf(
    val id: Int,
    val melding_id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val pdf: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PPdf

        if (id != other.id) return false
        if (melding_id != other.melding_id) return false
        if (uuid != other.uuid) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false
        if (!pdf.contentEquals(other.pdf)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + melding_id
        result = 31 * result + uuid.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + pdf.contentHashCode()
        return result
    }
}
