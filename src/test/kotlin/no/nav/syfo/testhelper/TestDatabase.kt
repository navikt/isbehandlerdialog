package no.nav.syfo.testhelper

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.melding.database.createMeldingFraBehandler
import no.nav.syfo.melding.domain.MeldingTilBehandler
import no.nav.syfo.melding.database.createMeldingTilBehandler
import no.nav.syfo.melding.domain.MeldingFraBehandler
import org.flywaydb.core.Flyway
import java.sql.Connection
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

fun DatabaseInterface.createMeldingerTilBehandler(meldingTilBehandler: MeldingTilBehandler, numberOfMeldinger: Int = 1): UUID {
    this.connection.use { connection ->
        for (i in 1..numberOfMeldinger) {
            connection.createMeldingTilBehandler(
                meldingTilBehandler = meldingTilBehandler
                    .copy(
                        uuid = UUID.randomUUID(),
                        tekst = "${meldingTilBehandler.tekst}$i"
                    ),
                commit = false,
            )
        }
        connection.commit()
    }
    return meldingTilBehandler.conversationRef
}

fun DatabaseInterface.createMeldingerFraBehandler(meldingFraBehandler: MeldingFraBehandler, numberOfMeldinger: Int = 1): UUID {
    this.connection.use { connection ->
        for (i in 1..numberOfMeldinger) {
            connection.createMeldingFraBehandler(
                meldingFraBehandler = meldingFraBehandler
                    .copy(
                        uuid = UUID.randomUUID(),
                        tekst = "${meldingFraBehandler.tekst}$i"
                    ),
                fellesformat = null,
                commit = false,
            )
        }
        connection.commit()
    }
    return meldingFraBehandler.conversationRef
}

fun DatabaseInterface.dropData() {
    val queryList = listOf(
        "DELETE FROM MELDING"
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
