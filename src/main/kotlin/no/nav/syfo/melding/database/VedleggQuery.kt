package no.nav.syfo.melding.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.melding.database.domain.PVedlegg
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
    meldingId: Int,
    number: Int,
    commit: Boolean = true,
): Int {
    val now = OffsetDateTime.now()
    val vedleggUuid = UUID.randomUUID()
    val idList = this.prepareStatement(queryCreateVedlegg).use {
        it.setInt(1, meldingId)
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

const val queryGetVedlegg =
    """
    SELECT vedlegg.* 
        FROM vedlegg INNER JOIN melding ON (vedlegg.melding_id = melding.id) 
        WHERE melding.uuid = ? 
        AND vedlegg.number=?
    """

fun DatabaseInterface.getVedlegg(
    uuid: UUID,
    number: Int,
): PVedlegg? =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetVedlegg).use {
            it.setString(1, uuid.toString())
            it.setInt(2, number)
            it.executeQuery().toList { toPVedlegg() }.firstOrNull()
        }
    }

fun ResultSet.toPVedlegg() =
    PVedlegg(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        melding_id = getInt("melding_id"),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java),
        number = getInt("number"),
        pdf = getBytes("pdf"),
    )
