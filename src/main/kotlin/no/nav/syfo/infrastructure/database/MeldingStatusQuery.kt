package no.nav.syfo.infrastructure.database

import no.nav.syfo.domain.MeldingStatus
import no.nav.syfo.infrastructure.database.domain.PMelding
import java.sql.*
import java.time.OffsetDateTime
import java.util.*

const val queryCreateMeldingStatus =
    """
        INSERT INTO MELDING_STATUS (
            id,
            uuid,
            melding_id,
            created_at,
            updated_at,
            status,
            tekst
        ) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?)
    """

fun Connection.createMeldingStatus(meldingStatus: MeldingStatus, meldingId: PMelding.Id) {
    val now = OffsetDateTime.now()
    val rowCount = this.prepareStatement(queryCreateMeldingStatus).use {
        it.setString(1, meldingStatus.uuid.toString())
        it.setInt(2, meldingId.id)
        it.setObject(3, now)
        it.setObject(4, now)
        it.setString(5, meldingStatus.status.name)
        it.setString(6, meldingStatus.tekst)
        it.executeUpdate()
    }
    if (rowCount != 1) {
        throw SQLException("Failed to create MeldingStatus with uuid: ${meldingStatus.uuid}")
    }
}

const val queryUpdateMeldingStatus =
    """
        UPDATE MELDING_STATUS SET status = ?, tekst = ?, updated_at = ?
        WHERE uuid = ?
    """

fun Connection.updateMeldingStatus(meldingStatus: MeldingStatus) {
    val rowCount = this.prepareStatement(queryUpdateMeldingStatus).use {
        it.setString(1, meldingStatus.status.name)
        it.setString(2, meldingStatus.tekst)
        it.setObject(3, OffsetDateTime.now())
        it.setString(4, meldingStatus.uuid.toString())
        it.executeUpdate()
    }
    if (rowCount != 1) {
        throw SQLException("Failed to update MeldingStatus with uuid: ${meldingStatus.uuid}")
    }
}
