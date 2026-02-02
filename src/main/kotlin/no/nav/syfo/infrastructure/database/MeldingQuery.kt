package no.nav.syfo.infrastructure.database

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.domain.DocumentComponentDTO
import no.nav.syfo.domain.Melding
import no.nav.syfo.domain.MeldingFraBehandler
import no.nav.syfo.domain.MeldingTilBehandler
import no.nav.syfo.domain.MeldingType
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.database.domain.PMelding
import no.nav.syfo.util.configuredJacksonMapper
import java.sql.*
import java.time.OffsetDateTime
import java.util.*

private val mapper = configuredJacksonMapper()

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

fun DatabaseInterface.hasMelding(msgId: String): Boolean {
    return this.connection.use { connection ->
        connection.getMeldingForMsgId(msgId) != null
    }
}

const val queryGetMeldingerWithTypeForConversationRefAndArbeidstakerident =
    """
        SELECT *
        FROM MELDING
        WHERE conversation_ref = ? AND arbeidstaker_personident = ? AND type = ? AND NOT innkommende
        ORDER BY tidspunkt ASC
    """

const val queryGetMeldingerForConversationRefAndArbeidstakerident =
    """
        SELECT *
        FROM MELDING
        WHERE (uuid = ? OR conversation_ref = ?) AND arbeidstaker_personident = ? AND NOT innkommende
        ORDER BY tidspunkt ASC
    """

fun Connection.getUtgaendeMeldingerInConversation(
    uuidParam: UUID,
    arbeidstakerPersonIdent: PersonIdent,
): MutableList<PMelding> {
    return this.prepareStatement(queryGetMeldingerForConversationRefAndArbeidstakerident).use {
        it.setString(1, uuidParam.toString())
        it.setString(2, uuidParam.toString())
        it.setString(3, arbeidstakerPersonIdent.value)
        it.executeQuery().toList { toPMelding() }
    }
}

fun Connection.getUtgaendeMeldingerInConversation(
    conversationRef: UUID,
    arbeidstakerPersonIdent: PersonIdent,
    type: MeldingType,
): List<PMelding> {
    return this.prepareStatement(queryGetMeldingerWithTypeForConversationRefAndArbeidstakerident).use {
        it.setString(1, conversationRef.toString())
        it.setString(2, arbeidstakerPersonIdent.value)
        it.setString(3, type.name)
        it.executeQuery().toList { toPMelding() }
    }
}

const val queryGetUtgaendeMeldingForTypeAndArbeidstakerident =
    """
        SELECT *
        FROM MELDING
        WHERE type = ? AND arbeidstaker_personident = ? AND NOT innkommende
        ORDER BY tidspunkt ASC
    """

fun Connection.getUtgaendeMeldingerWithType(
    meldingType: MeldingType,
    arbeidstakerPersonIdent: String,
): List<PMelding> {
    return this.prepareStatement(queryGetUtgaendeMeldingForTypeAndArbeidstakerident).use {
        it.setString(1, meldingType.name)
        it.setString(2, arbeidstakerPersonIdent)
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

const val queryGetUnpublishedAvvisteMeldinger =
    """
        SELECT *
        FROM melding m
        INNER JOIN melding_status ms on (m.id = ms.melding_id)
        WHERE ms.status = 'AVVIST' AND m.avvist_published_at IS NULL
    """

fun DatabaseInterface.getUnpublishedAvvisteMeldinger(): List<PMelding> =
    connection.use { connection ->
        connection.prepareStatement(queryGetUnpublishedAvvisteMeldinger).use {
            it.executeQuery().toList { toPMelding() }
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

const val queryUpdateAvvistMeldingPublishedAt =
    """
        UPDATE MELDING
        SET avvist_published_at = ?
        WHERE uuid = ?
    """

fun DatabaseInterface.updateAvvistMeldingPublishedAt(uuid: UUID) =
    connection.use { connection ->
        connection.prepareStatement(queryUpdateAvvistMeldingPublishedAt).use {
            it.setObject(1, OffsetDateTime.now())
            it.setString(2, uuid.toString())
            val updated = it.executeUpdate()
            if (updated != 1) {
                throw SQLException("Expected a single row to be updated, got update count $updated")
            }
        }
        connection.commit()
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
            journalpost_id,
            ubesvart_published_at,
            veileder_ident,
            avvist_published_at
        ) VALUES(DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?) RETURNING id
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
): PMelding.Id {
    return this.createMelding(
        melding = meldingTilBehandler,
        commit = commit,
    )
}

fun Connection.createMeldingFraBehandler(
    meldingFraBehandler: MeldingFraBehandler,
    fellesformat: String? = null,
    commit: Boolean = false,
): PMelding.Id {
    return this.createMelding(
        melding = meldingFraBehandler,
        fellesformat = fellesformat,
        commit = commit,
    )
}

private fun Connection.createMelding(
    melding: Melding,
    fellesformat: String? = null,
    commit: Boolean = true,
): PMelding.Id {
    val idList = this.prepareStatement(queryCreateMelding).use {
        it.setString(1, melding.uuid.toString())
        it.setObject(2, OffsetDateTime.now())
        it.setBoolean(3, melding.innkommende)
        it.setString(4, melding.type.name)
        it.setString(5, melding.conversationRef.toString())
        it.setString(6, melding.parentRef?.toString())
        it.setString(7, melding.msgId)
        it.setObject(8, melding.tidspunkt)
        it.setString(9, melding.arbeidstakerPersonIdent.value)
        it.setString(10, melding.behandlerPersonIdent?.value)
        it.setString(11, melding.behandlerRef?.toString())
        it.setString(12, melding.tekst)
        it.setObject(13, mapper.writeValueAsString(melding.document))
        it.setInt(14, melding.antallVedlegg)
        it.setString(15, melding.behandlerNavn)
        it.setNull(16, Types.TIMESTAMP_WITH_TIMEZONE)
        it.setString(17, melding.journalpostId)
        it.setNull(18, Types.TIMESTAMP_WITH_TIMEZONE)
        it.setString(19, melding.veilederIdent)
        it.setNull(20, Types.TIMESTAMP_WITH_TIMEZONE)
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
    return PMelding.Id(id)
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

fun DatabaseInterface.updateMeldingJournalpostId(melding: MeldingTilBehandler, journalpostId: String) {
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

const val queryUpdateArbeidstakerPersonident = """
    UPDATE melding
    SET arbeidstaker_personident = ?
    WHERE id = ?
"""

fun DatabaseInterface.updateArbeidstakerPersonident(meldinger: List<PMelding>, personident: PersonIdent) {
    connection.use { connection ->
        connection.prepareStatement(queryUpdateArbeidstakerPersonident).use {
            meldinger.forEach { melding ->
                it.setString(1, personident.value)
                it.setInt(2, melding.id.id)
                val updated = it.executeUpdate()
                if (updated != 1) {
                    throw SQLException("Expected a single row to be updated, got update count $updated")
                }
            }
        }
        connection.commit()
    }
}

fun ResultSet.toPMelding() =
    PMelding(
        id = PMelding.Id(getInt("id")),
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
        ubesvartPublishedAt = getObject("ubesvart_published_at", OffsetDateTime::class.java),
        veilederIdent = getString("veileder_ident"),
        avvistPublishedAt = getObject("avvist_published_at", OffsetDateTime::class.java),
    )
