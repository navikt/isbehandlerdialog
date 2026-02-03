package no.nav.syfo.infrastructure.database.repository

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.application.IMeldingRepository
import no.nav.syfo.domain.DocumentComponentDTO
import no.nav.syfo.domain.PersonIdent
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

    override fun getMeldingerForArbeidstaker(arbeidstakerPersonIdent: PersonIdent): List<PMelding> =
        database.connection.use { connection ->
            connection.prepareStatement(QUERY_GET_MELDING_FOR_ARBEIDSTAKER_PERSONIDENT).use {
                it.setString(1, arbeidstakerPersonIdent.value)
                it.executeQuery().toList { toPMelding() }
            }
        }

    override suspend fun getUbesvarteMeldinger(fristDato: OffsetDateTime): List<PMelding> =
        database.connection.use { connection ->
            connection.prepareStatement(QUERY_GET_UBESVARTE_MELDINGER).use {
                it.setObject(1, fristDato)
                it.executeQuery().toList { toPMelding() }
            }
        }

    override suspend fun updateUbesvartPublishedAt(uuid: UUID) {
        database.connection.use { connection ->
            val rowCount = connection.prepareStatement(QUERY_UPDATE_UBESVART_PUBLISHED_AT).use {
                it.setObject(1, OffsetDateTime.now())
                it.setString(2, uuid.toString())
                it.executeUpdate()
            }
            if (rowCount != 1) {
                throw SQLException("Failed to update published_at for ubesvart melding with uuid: $uuid ")
            }
            connection.commit()
        }
    }

    companion object {
        private const val QUERY_GET_MELDING_FOR_UUID =
            """
                SELECT *
                FROM MELDING
                WHERE uuid = ?
            """

        private const val QUERY_GET_MELDING_FOR_ARBEIDSTAKER_PERSONIDENT =
            """
                SELECT *
                FROM MELDING
                WHERE arbeidstaker_personident = ?
                ORDER BY id ASC
            """

        private const val QUERY_GET_UBESVARTE_MELDINGER =
            """
                SELECT DISTINCT m_utgaende.*
                FROM MELDING m_utgaende
                WHERE NOT m_utgaende.innkommende
                    AND m_utgaende.ubesvart_published_at IS NULL
                    AND m_utgaende.created_at <= ?
                    AND m_utgaende.type not in ('FORESPORSEL_PASIENT_PAMINNELSE', 'HENVENDELSE_MELDING_FRA_NAV')
                    AND NOT EXISTS (
                        SELECT melding_id
                        FROM melding_status
                        WHERE melding_id = m_utgaende.id AND status = 'AVVIST'
                    )
                    AND NOT EXISTS (
                        SELECT id
                        FROM MELDING m_innkommende
                        WHERE m_innkommende.conversation_ref = m_utgaende.conversation_ref
                            AND m_innkommende.innkommende
                            AND m_innkommende.created_at > m_utgaende.created_at
                    )
            """

        private const val QUERY_UPDATE_UBESVART_PUBLISHED_AT =
            """
                UPDATE MELDING
                SET ubesvart_published_at = ?
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
