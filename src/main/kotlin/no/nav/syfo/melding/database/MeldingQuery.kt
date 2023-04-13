package no.nav.syfo.melding.database

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.database.domain.PMelding
import no.nav.syfo.melding.domain.*
import no.nav.syfo.util.configuredJacksonMapper
import java.sql.*
import java.time.OffsetDateTime
import java.util.*

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

const val queryGetMeldingForMsgId =
    """
        SELECT *
        FROM MELDING
        WHERE msg_id = ?
    """

fun Connection.getMeldingForMsgId(
    msgId: String,
): PMelding? {
    return this.prepareStatement(queryGetMeldingForMsgId).use {
        it.setString(1, msgId)
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

const val queryGetUnpublishedMeldingerFraBehandler =
    """
        SELECT *
        FROM MELDING
        WHERE innkommende AND innkommende_published_at IS NULL
        ORDER BY created_at ASC
    """

fun DatabaseInterface.getUnpublishedMeldingerFraBehandler(): List<PMelding> {
    return connection.use { connection ->
        connection.prepareStatement(queryGetUnpublishedMeldingerFraBehandler).use {
            it.executeQuery().toList { toPMelding() }
        }
    }
}

const val queryUpdateInnkommendePublishedAt =
    """
        UPDATE MELDING
        SET innkommende_published_at = ?
        WHERE uuid = ?
    """

fun DatabaseInterface.updateInnkommendePublishedAt(uuid: UUID) {
    connection.use { connection ->
        val rowCount = connection.prepareStatement(queryUpdateInnkommendePublishedAt).use {
            it.setObject(1, OffsetDateTime.now())
            it.setString(2, uuid.toString())
            it.executeUpdate()
        }
        if (rowCount != 1) {
            throw SQLException("Failed to save published at for meldingFraBehandler with uuid: $uuid ")
        }
        connection.commit()
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
            msg_id,
            tidspunkt,
            arbeidstaker_personident,
            behandler_personident,
            behandler_ref,
            tekst,
            document,
            antall_vedlegg,
            behandler_navn,
            innkommende_published_at,
            journalpost_id
        ) VALUES(DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?) RETURNING id
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
        it.setString(7, pMelding.msgId)
        it.setObject(8, pMelding.tidspunkt)
        it.setString(9, pMelding.arbeidstakerPersonIdent)
        it.setString(10, pMelding.behandlerPersonIdent)
        it.setString(11, pMelding.behandlerRef?.toString())
        it.setString(12, pMelding.tekst)
        it.setObject(13, mapper.writeValueAsString(pMelding.document))
        it.setInt(14, pMelding.antallVedlegg)
        it.setString(15, pMelding.behandlerNavn)
        it.setNull(16, Types.TIMESTAMP_WITH_TIMEZONE)
        it.setString(17, pMelding.journalpostId)
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

const val queryGetMeldingerTilBehandlerWithoutJournalpostId = """
    SELECT m.*, p.pdf as pdf
    FROM melding m
    INNER JOIN pdf p on m.id = p.melding_id
    WHERE m.journalpost_id IS NULL 
    AND m.innkommende = false 
"""

fun DatabaseInterface.getIkkeJournalforteMeldingerTilBehandler(): List<Pair<PMelding, ByteArray>> {
    return connection.use { connection ->
        connection.prepareStatement(queryGetMeldingerTilBehandlerWithoutJournalpostId).use {
            it.executeQuery().toList { Pair(toPMelding(), getBytes("pdf")) }
        }
    }
}

const val queryUpdateJournalpostId = """
    UPDATE melding
    SET journalpost_id = ?
    WHERE uuid = ?
"""

fun DatabaseInterface.updateMeldingJournalpostId(melding: PMelding, journalpostId: String) {
    connection.use { connection ->
        connection.prepareStatement(queryUpdateJournalpostId).use {
            it.setString(1, journalpostId)
            it.setString(2, melding.uuid.toString())
            val updated = it.executeUpdate()
            if (updated != 1) {
                throw SQLException("Expected a single row to be updated, got update count $updated")
            }
        }
        connection.commit()
    }
}

fun ResultSet.toPMelding() =
    PMelding(
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        innkommende = getBoolean("innkommende"),
        type = getString("type"),
        conversationRef = getString("conversation_ref")!!.let { UUID.fromString(it) },
        parentRef = getString("parent_ref")?.let { UUID.fromString(it) },
        msgId = getString("msg_id"),
        tidspunkt = getObject("tidspunkt", OffsetDateTime::class.java),
        arbeidstakerPersonIdent = getString("arbeidstaker_personident"),
        behandlerPersonIdent = getString("behandler_personident"),
        behandlerNavn = getString("behandler_navn"),
        behandlerRef = getString("behandler_ref")?.let { UUID.fromString(it) },
        tekst = getString("tekst"),
        document = mapper.readValue(getString("document"), object : TypeReference<List<DocumentComponentDTO>>() {}),
        antallVedlegg = getInt("antall_vedlegg"),
        innkommendePublishedAt = getObject("innkommende_published_at", OffsetDateTime::class.java),
        journalpostId = getString("journalpost_id"),
    )
