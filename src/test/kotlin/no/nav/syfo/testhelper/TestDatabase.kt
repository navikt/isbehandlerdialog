package no.nav.syfo.testhelper

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.melding.database.*
import no.nav.syfo.melding.database.domain.PMelding
import no.nav.syfo.melding.database.domain.PPdf
import no.nav.syfo.melding.domain.MeldingFraBehandler
import no.nav.syfo.melding.domain.MeldingTilBehandler
import no.nav.syfo.melding.status.database.PMeldingStatus
import no.nav.syfo.melding.status.database.toPMeldingStatus
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.SQLException
import java.time.OffsetDateTime
import java.util.*

class TestDatabase : DatabaseInterface {
    private val pg: EmbeddedPostgres = try {
        EmbeddedPostgres.start()
    } catch (e: Exception) {
        EmbeddedPostgres.builder().setLocaleConfig("locale", "en_US").start()
    }

    override val connection: Connection
        get() = pg.postgresDatabase.connection.apply { autoCommit = false }

    init {

        Flyway.configure().run {
            dataSource(pg.postgresDatabase).load().migrate()
        }
    }

    fun stop() {
        pg.close()
    }
}

fun DatabaseInterface.createMeldingerTilBehandler(
    meldingTilBehandler: MeldingTilBehandler,
    numberOfMeldinger: Int = 1,
): Pair<UUID, List<PMelding.Id>> {
    val idList = mutableListOf<PMelding.Id>()
    this.connection.use { connection ->
        for (i in 1..numberOfMeldinger) {
            val id = connection.createMeldingTilBehandler(
                meldingTilBehandler = meldingTilBehandler
                    .copy(
                        uuid = UUID.randomUUID(),
                        tekst = "${meldingTilBehandler.tekst}$i",
                    ),
                commit = false,
            )
            idList.add(id)
        }
        connection.commit()
    }
    return Pair(meldingTilBehandler.conversationRef, idList)
}

fun DatabaseInterface.createMeldingerFraBehandler(
    meldingFraBehandler: MeldingFraBehandler,
    numberOfMeldinger: Int = 1,
): Pair<UUID, List<PMelding.Id>> {
    val idList = mutableListOf<PMelding.Id>()
    this.connection.use { connection ->
        for (i in 1..numberOfMeldinger) {
            val id = connection.createMeldingFraBehandler(
                meldingFraBehandler = meldingFraBehandler
                    .copy(
                        uuid = UUID.randomUUID(),
                        tekst = "${meldingFraBehandler.tekst}$i"
                    ),
                fellesformat = null,
            )
            idList.add(id)
        }
        connection.commit()
    }
    return Pair(meldingFraBehandler.conversationRef, idList)
}

const val queryUpdateCreatedAt =
    """
        UPDATE MELDING
        SET created_at = ?
        WHERE id = ?
    """

fun DatabaseInterface.updateMeldingCreatedAt(
    id: PMelding.Id,
    createdAt: OffsetDateTime,
) {
    this.connection.use { connection ->
        val rowCount = connection.prepareStatement(queryUpdateCreatedAt).use {
            it.setObject(1, createdAt)
            it.setInt(2, id.id)
            it.executeUpdate()
        }
        if (rowCount != 1) {
            throw SQLException("Failed to update createdAt with id: $id ")
        }
        connection.commit()
    }
}

const val queryUpdateAvvistMeldingPublishedAt =
    """
        UPDATE MELDING
        SET avvist_published_at = ?
        WHERE id = ?
    """

fun DatabaseInterface.updateAvvistMeldingPublishedAt(id: PMelding.Id) =
    connection.use { connection ->
        connection.prepareStatement(queryUpdateAvvistMeldingPublishedAt).use {
            it.setObject(1, OffsetDateTime.now())
            it.setInt(2, id.id)
            val updated = it.executeUpdate()
            if (updated != 1) {
                throw SQLException("Expected a single row to be updated, got update count $updated")
            }
        }
        connection.commit()
    }

const val queryGetPDFs = """
    SELECT p.*
    FROM pdf AS p INNER JOIN melding m on p.melding_id = m.id
    WHERE m.uuid = ?
"""

fun DatabaseInterface.getPDFs(meldingUuid: UUID): List<PPdf> = this.connection.use { connection ->
    connection.prepareStatement(queryGetPDFs).use {
        it.setString(1, meldingUuid.toString())
        it.executeQuery().toList { toPPdf() }
    }
}

fun DatabaseInterface.firstPdf(meldingUuid: UUID): PPdf = this.getPDFs(meldingUuid).first()

fun DatabaseInterface.getMeldingStatus(): List<PMeldingStatus> = this.connection.use { connection ->
    connection.prepareStatement("SELECT * FROM MELDING_STATUS").use {
        it.executeQuery().toList { toPMeldingStatus() }
    }
}

fun DatabaseInterface.dropData() {
    val queryList = listOf(
        "DELETE FROM MELDING",
        "DELETE FROM PDF",
        "DELETE FROM VEDLEGG",
        "DELETE FROM MELDING_STATUS",
    )
    this.connection.use { connection ->
        queryList.forEach { query ->
            connection.prepareStatement(query).execute()
        }
        connection.commit()
    }
}

class TestDatabaseNotResponding : DatabaseInterface {

    override val connection: Connection
        get() = throw Exception("Not working")

    fun stop() {
    }
}
