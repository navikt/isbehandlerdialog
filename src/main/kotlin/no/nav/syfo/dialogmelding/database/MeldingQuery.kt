package no.nav.syfo.dialogmelding.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmelding.database.domain.PMelding
import no.nav.syfo.domain.PersonIdent
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.time.OffsetDateTime
import java.util.UUID

const val queryGetMeldingForArbeidstakerPersonident =
    """
        SELECT *
        FROM MELDING
        WHERE arbeidstaker_personident = ?
    """

fun DatabaseInterface.getMeldingForArbeidstakerPersonident(
    arbeidstakerPersonident: PersonIdent
): List<PMelding> {
    return connection.use { connection ->
        connection.prepareStatement(queryGetMeldingForArbeidstakerPersonident).use {
            it.setString(1, arbeidstakerPersonident.value)
            it.executeQuery().toList { toPMelding() }
        }
    }
}

const val queryCreateMelding =
    """
        INSERT INTO MELDING ( 
            id,
            uuid,
            created_at,
            innkommende,
            type,
            conversation,
            parent,
            tidspunkt,
            arbeidstaker_personident,
            behandler_personident,
            behandler_ref,
            tekst,
            antall_vedlegg
        ) VALUES(DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
    """

const val queryCreateMeldingFellesformat =
    """
        INSERT INTO MELDING_FELLESFORMAT (
            id,
            melding_id,
            fellesformat
        ) VALUES (DEFAULT, ?, ?) RETURNING id
    """

fun Connection.createNewMelding(
    melding: PMelding,
    fellesformat: String? = null,
    commit: Boolean = true,
): Int {
    val idList = this.prepareStatement(queryCreateMelding).use {
        it.setString(1, melding.uuid.toString())
        it.setObject(2, OffsetDateTime.now())
        it.setBoolean(3, melding.innkommende)
        it.setString(4, melding.type)
        it.setString(5, melding.conversation.toString())
        it.setString(6, melding.parent?.toString())
        it.setObject(7, melding.tidspunkt)
        it.setString(8, melding.arbeidstakerPersonIdent.value)
        it.setString(9, melding.behandlerPersonIdent?.value)
        it.setString(10, melding.behandlerRef?.toString())
        it.setString(11, melding.tekst)
        it.setInt(12, melding.antallVedlegg)
        it.executeQuery().toList { getInt("id") }
    }
    if (idList.size != 1) {
        throw SQLException("Creating DialogmeldingIn failed, no rows affected.")
    }
    val id = idList.first()
    if (fellesformat != null) {
        this.prepareStatement(queryCreateMeldingFellesformat).use {
            it.setInt(1, id)
            it.setString(2, fellesformat)
            it.executeQuery()
        }
    }
    if (commit) {
        this.commit()
    }
    return id
}

fun ResultSet.toPMelding() =
    PMelding(
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        innkommende = getBoolean("innkommende"),
        type = getString("type"),
        conversation = getString("conversation")!!.let { UUID.fromString(it) },
        parent = getString("parent")?.let { UUID.fromString(it) },
        tidspunkt = getObject("tidspunkt", OffsetDateTime::class.java),
        arbeidstakerPersonIdent = PersonIdent(getString("arbeidstaker_personident")),
        behandlerPersonIdent = getString("behandler_personident")?.let { PersonIdent(it) },
        behandlerRef = getString("behandler_ref")?.let { UUID.fromString(it) },
        tekst = getString("tekst"),
        antallVedlegg = getInt("antall_vedlegg"),
    )
