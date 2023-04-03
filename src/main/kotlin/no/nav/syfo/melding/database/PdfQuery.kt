package no.nav.syfo.melding.database

import no.nav.syfo.application.database.toList
import java.sql.Connection
import java.sql.SQLException
import java.time.OffsetDateTime
import java.util.*

const val queryCreatePdf =
    """
    INSERT INTO pdf (
        id,
        melding_id,
        uuid,
        created_at,
        updated_at,
        pdf) VALUES (DEFAULT, ?, ?, ?, ?, ?) RETURNING id
    """

fun Connection.createPdf(
    pdf: ByteArray,
    meldingId: Int,
    commit: Boolean = true,
): Int {
    val now = OffsetDateTime.now()
    val pdfUuid = UUID.randomUUID()
    val idList = this.prepareStatement(queryCreatePdf).use {
        it.setInt(1, meldingId)
        it.setString(2, pdfUuid.toString())
        it.setObject(3, now)
        it.setObject(4, now)
        it.setBytes(5, pdf)
        it.executeQuery().toList { getInt("id") }
    }
    if (idList.size != 1) {
        throw SQLException("Creating Pdf failed, no rows affected.")
    }
    if (commit) {
        this.commit()
    }
    return idList.first()
}
