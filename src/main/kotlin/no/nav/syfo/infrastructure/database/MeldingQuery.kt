package no.nav.syfo.infrastructure.database

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.domain.DocumentComponentDTO
import no.nav.syfo.domain.Melding
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.database.domain.PMelding
import no.nav.syfo.util.configuredJacksonMapper
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
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
    type: Melding.MeldingType,
): List<PMelding> {
    return this.prepareStatement(queryGetMeldingerWithTypeForConversationRefAndArbeidstakerident).use {
        it.setString(1, conversationRef.toString())
        it.setString(2, arbeidstakerPersonIdent.value)
        it.setString(3, type.name)
        it.executeQuery().toList { toPMelding() }
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
