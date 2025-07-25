package no.nav.syfo.infrastructure.database.repository

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.application.IMeldingRepository
import no.nav.syfo.domain.DocumentComponentDTO
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.domain.PMelding
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.util.configuredJacksonMapper
import java.sql.*
import java.time.OffsetDateTime
import java.util.*

private val mapper = configuredJacksonMapper()

class MeldingRepository(private val database: DatabaseInterface) : IMeldingRepository {

    override suspend fun getMelding(uuid: UUID): PMelding? =
        database.connection.use { connection ->
            connection.prepareStatement(QUERY_GET_MELDING_FOR_UUID).use {
                it.setString(1, uuid.toString())
                it.executeQuery().toList { toPMelding() }.firstOrNull()
            }
        }

    companion object {
        private const val QUERY_GET_MELDING_FOR_UUID =
            """
                SELECT *
                FROM MELDING
                WHERE uuid = ?
            """
    }
}

private fun ResultSet.toPMelding() =
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
