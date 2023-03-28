package no.nav.syfo.melding.database

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.melding.database.domain.PMelding
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.domain.*
import no.nav.syfo.util.configuredJacksonMapper
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.time.OffsetDateTime
import java.util.UUID

private val mapper = configuredJacksonMapper()

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
            document,
            antall_vedlegg
        ) VALUES(DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?) RETURNING id
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
        pMelding = meldingTilBehandler.toPMelding(),
        commit = commit,
    )
}

fun Connection.createMeldingFraBehandler(
    meldingFraBehandler: MeldingFraBehandler,
    fellesformat: String?,
    commit: Boolean = true,
): Int {
    return this.createMelding(
        pMelding = meldingFraBehandler.toPMelding(),
        fellesformat = fellesformat,
        commit = commit,
    )
}

private fun Connection.createMelding(
    pMelding: PMelding,
    fellesformat: String? = null,
    commit: Boolean = true,
): Int {
    val idList = this.prepareStatement(queryCreateMelding).use {
        it.setString(1, pMelding.uuid.toString())
        it.setObject(2, OffsetDateTime.now())
        it.setBoolean(3, pMelding.innkommende)
        it.setString(4, pMelding.type)
        it.setString(5, pMelding.conversationRef.toString())
        it.setString(6, pMelding.parentRef?.toString())
        it.setObject(7, pMelding.tidspunkt)
        it.setString(8, pMelding.arbeidstakerPersonIdent)
        it.setString(9, pMelding.behandlerPersonIdent)
        it.setString(10, pMelding.behandlerRef?.toString())
        it.setString(11, pMelding.tekst)
        it.setObject(12, mapper.writeValueAsString(pMelding.document))
        it.setInt(13, pMelding.antallVedlegg)
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
        document = mapper.readValue(getString("document"), object : TypeReference<List<DocumentComponentDTO>>() {}),
        antallVedlegg = getInt("antall_vedlegg"),
    )
