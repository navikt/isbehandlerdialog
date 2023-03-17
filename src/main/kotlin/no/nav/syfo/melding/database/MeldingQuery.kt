package no.nav.syfo.melding.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.melding.domain.MeldingTilBehandler
import no.nav.syfo.melding.domain.toPMelding
import no.nav.syfo.melding.database.domain.PMelding
import no.nav.syfo.melding.domain.MeldingFraBehandler
import no.nav.syfo.domain.PersonIdent
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.time.OffsetDateTime
import java.util.UUID

const val queryGetMeldingForArbeidstakerPersonIdent =
    """
        SELECT *
        FROM MELDING
        WHERE arbeidstaker_personident = ?
        ORDER BY id ASC
    """

fun DatabaseInterface.getMeldingerForArbeidstaker(
    arbeidstakerPersonIdent: PersonIdent
): List<PMelding> {
    return connection.use { connection ->
        connection.prepareStatement(queryGetMeldingForArbeidstakerPersonIdent).use {
            it.setString(1, arbeidstakerPersonIdent.value)
            it.executeQuery().toList { toPMelding() }
        }
    }
}

const val queryGetMeldingForUUID =
    """
        SELECT *
        FROM MELDING
        WHERE uuid = ?
    """

fun Connection.getMelding(
    uuid: UUID,
): PMelding? {
    return this.prepareStatement(queryGetMeldingForUUID).use {
        it.setString(1, uuid.toString())
        it.executeQuery().toList { toPMelding() }.firstOrNull()
    }
}

const val queryGetMeldingForConversationRefAndArbeidstakerident =
    """
        SELECT *
        FROM MELDING
        WHERE conversation_ref = ? AND arbeidstaker_personident = ? AND NOT innkommende
        ORDER BY tidspunkt ASC
    """

fun Connection.hasSendtMeldingForConversationRefAndArbeidstakerIdent(
    conversationRef: UUID,
    arbeidstakerPersonIdent: PersonIdent,
): Boolean {
    return this.getUtgaendeMeldingerInConversation(
        conversationRef = conversationRef,
        arbeidstakerPersonIdent = arbeidstakerPersonIdent,
    ).isNotEmpty()
}

fun Connection.getUtgaendeMeldingerInConversation(
    conversationRef: UUID,
    arbeidstakerPersonIdent: PersonIdent,
): List<PMelding> {
    return this.prepareStatement(queryGetMeldingForConversationRefAndArbeidstakerident).use {
        it.setString(1, conversationRef.toString())
        it.setString(2, arbeidstakerPersonIdent.value)
        it.executeQuery().toList { toPMelding() }
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
            conversation_ref,
            parent_ref,
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

fun Connection.createMeldingTilBehandler(
    meldingTilBehandler: MeldingTilBehandler,
    commit: Boolean = true,
): Int {
    return this.createMelding(
        melding = meldingTilBehandler.toPMelding(),
        commit = commit,
    )
}

fun Connection.createMeldingFraBehandler(
    meldingFraBehandler: MeldingFraBehandler,
    fellesformat: String?,
    commit: Boolean = true,
): Int {
    return this.createMelding(
        melding = meldingFraBehandler.toPMelding(),
        fellesformat = fellesformat,
        commit = commit,
    )
}

private fun Connection.createMelding(
    melding: PMelding,
    fellesformat: String? = null,
    commit: Boolean = true,
): Int {
    val idList = this.prepareStatement(queryCreateMelding).use {
        it.setString(1, melding.uuid.toString())
        it.setObject(2, OffsetDateTime.now())
        it.setBoolean(3, melding.innkommende)
        it.setString(4, melding.type)
        it.setString(5, melding.conversationRef.toString())
        it.setString(6, melding.parentRef?.toString())
        it.setObject(7, melding.tidspunkt)
        it.setString(8, melding.arbeidstakerPersonIdent)
        it.setString(9, melding.behandlerPersonIdent)
        it.setString(10, melding.behandlerRef?.toString())
        it.setString(11, melding.tekst)
        it.setInt(12, melding.antallVedlegg)
        it.executeQuery().toList { getInt("id") }
    }
    if (idList.size != 1) {
        throw SQLException("Creating Melding failed, no rows affected.")
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
        conversationRef = getString("conversation_ref")!!.let { UUID.fromString(it) },
        parentRef = getString("parent_ref")?.let { UUID.fromString(it) },
        tidspunkt = getObject("tidspunkt", OffsetDateTime::class.java),
        arbeidstakerPersonIdent = getString("arbeidstaker_personident"),
        behandlerPersonIdent = getString("behandler_personident"),
        behandlerRef = getString("behandler_ref")?.let { UUID.fromString(it) },
        tekst = getString("tekst"),
        antallVedlegg = getInt("antall_vedlegg"),
    )
