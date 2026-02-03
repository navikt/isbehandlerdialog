package no.nav.syfo.infrastructure.database

import no.nav.syfo.infrastructure.database.domain.PMelding
import java.sql.*
import java.time.OffsetDateTime
import java.util.*

const val queryCreateVedlegg =
    """
    INSERT INTO vedlegg (
        id,
        melding_id,
        uuid,
        created_at,
        updated_at,
        number,
        pdf) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?) RETURNING id
    """

fun Connection.createVedlegg(
    pdf: ByteArray,
    meldingId: PMelding.Id,
    number: Int,
    commit: Boolean = true,
): Int {
    val now = OffsetDateTime.now()
    val vedleggUuid = UUID.randomUUID()
    val idList = this.prepareStatement(queryCreateVedlegg).use {
        it.setInt(1, meldingId.id)
        it.setString(2, vedleggUuid.toString())
        it.setObject(3, now)
        it.setObject(4, now)
        it.setInt(5, number)
        it.setBytes(6, pdf)
        it.executeQuery().toList { getInt("id") }
    }
    if (idList.size != 1) {
        throw SQLException("Creating vedlegg failed, no rows affected.")
    }
    if (commit) {
        this.commit()
    }
    return idList.first()
}
