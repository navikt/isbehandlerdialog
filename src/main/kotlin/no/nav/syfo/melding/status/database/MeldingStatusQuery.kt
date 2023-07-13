package no.nav.syfo.melding.status.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.melding.database.domain.PMelding
import no.nav.syfo.melding.status.domain.MeldingStatus
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
            tekst,
            avvist_published_at
        ) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?)
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
        it.setNull(7, Types.TIMESTAMP_WITH_TIMEZONE)
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

const val queryGetMeldingStatusForMeldingId =
    """
        SELECT *
        FROM MELDING_STATUS
        WHERE melding_id = ?
    """

fun DatabaseInterface.getMeldingStatus(meldingId: PMelding.Id, connection: Connection? = null): PMeldingStatus? {
    return connection?.getMeldingStatus(
        meldingId = meldingId,
    )
        ?: this.connection.use {
            it.getMeldingStatus(
                meldingId = meldingId,
            )
        }
}

const val queryGetUnpublishedAvvistMeldingStatus =
    """
        SELECT melding_id
        FROM MELDING_STATUS
        WHERE status = 'AVVIST' AND avvist_published_at IS NULL
    """

fun DatabaseInterface.getUnpublishedAvvistMeldingStatus(): List<PMelding.Id> =
    connection.use { connection ->
        connection.prepareStatement(queryGetUnpublishedAvvistMeldingStatus).use {
            it.executeQuery().toList { toPMeldingId() }
        }
    }

const val queryUpdateAvvistMeldingPublishedAt =
    """
        UPDATE MELDING_STATUS
        SET avvist_published_at = ?
        WHERE melding_id = ?
    """

fun DatabaseInterface.updateAvvistMeldingPublishedAt(meldingId: PMelding.Id) =
    connection.use { connection ->
        connection.prepareStatement(queryUpdateAvvistMeldingPublishedAt).use {
            it.setObject(1, OffsetDateTime.now())
            it.setInt(2, meldingId.id)
            val updated = it.executeUpdate()
            if (updated != 1) {
                throw SQLException("Expected a single row to be updated, got update count $updated")
            }
        }
        connection.commit()
    }

fun Connection.getMeldingStatus(meldingId: PMelding.Id): PMeldingStatus? {
    return this.prepareStatement(queryGetMeldingStatusForMeldingId).use {
        it.setInt(1, meldingId.id)
        it.executeQuery().toList { toPMeldingStatus() }.firstOrNull()
    }
}

fun ResultSet.toPMeldingStatus() =
    PMeldingStatus(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        meldingId = PMelding.Id(getInt("melding_id")),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java),
        status = getString("status"),
        tekst = getString("tekst"),
        avvistPublishedAt = getObject("avvist_published_at", OffsetDateTime::class.java),
    )

fun ResultSet.toPMeldingId() =
    PMelding.Id(id = getInt("melding_id"))
