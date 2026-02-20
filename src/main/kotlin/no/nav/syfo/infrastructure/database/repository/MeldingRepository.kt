package no.nav.syfo.infrastructure.database.repository

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.application.IMeldingRepository
import no.nav.syfo.domain.DocumentComponentDTO
import no.nav.syfo.domain.Melding
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.domain.PMelding
import no.nav.syfo.infrastructure.database.domain.PVedlegg
import no.nav.syfo.infrastructure.database.domain.toMeldingFraBehandler
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

    override fun createMeldingTilBehandler(
        meldingTilBehandler: Melding.MeldingTilBehandler,
        connection: Connection?,
    ): PMelding.Id =
        if (connection != null) {
            connection.createMelding(melding = meldingTilBehandler, shouldCommit = false)
        } else {
            database.connection.use { connection ->
                connection.createMelding(
                    melding = meldingTilBehandler,
                    shouldCommit = true,
                )
            }
        }

    override fun createMeldingFraBehandler(
        meldingFraBehandler: Melding.MeldingFraBehandler,
        fellesformat: String?,
        connection: Connection?,
    ): PMelding.Id =
        if (connection != null) {
            connection.createMelding(
                melding = meldingFraBehandler,
                fellesformat = fellesformat,
                shouldCommit = false,
            )
        } else {
            database.connection.use { connection ->
                connection.createMelding(
                    melding = meldingFraBehandler,
                    fellesformat = fellesformat,
                    shouldCommit = true,
                )
            }
        }

    private fun Connection.createMelding(
        melding: Melding,
        fellesformat: String? = null,
        shouldCommit: Boolean,
    ): PMelding.Id {
        val idList = this.prepareStatement(QUERY_CREATE_MELDING).use {
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
            this.prepareStatement(QUERY_CREATE_MELDING_FELLESFORMAT).use {
                it.setInt(1, id)
                it.setString(2, fellesformat)
                it.executeQuery()
            }
        }
        if (shouldCommit) {
            this.commit()
        }
        return PMelding.Id(id)
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

    override fun updateInnkommendePublishedAt(uuid: UUID) {
        database.connection.use { connection ->
            val rowCount = connection.prepareStatement(QUERY_UPDATE_INNKOMMENDE_PUBLISHED_AT).use {
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

    override fun getVedlegg(uuid: UUID, number: Int): PVedlegg? =
        database.connection.use { connection ->
            connection.prepareStatement(QUERY_GET_VEDLEGG).use {
                it.setString(1, uuid.toString())
                it.setInt(2, number)
                it.executeQuery().toList { toPVedlegg() }.firstOrNull()
            }
        }

    override fun getUnpublishedMeldingerFraBehandler(): List<Melding.MeldingFraBehandler> =
        database.connection.use { connection ->
            connection.prepareStatement(QUERY_GET_UNPUBLISHED_MELDINGER_FRA_BEHANDLER).use {
                it.executeQuery().toList { toPMelding().toMeldingFraBehandler() }
            }
        }

    override fun createVedlegg(pdf: ByteArray, meldingId: PMelding.Id, number: Int, connection: Connection): Int {
        val now = OffsetDateTime.now()
        val vedleggUuid = UUID.randomUUID()
        val idList = connection.prepareStatement(QUERY_CREATE_VEDLEGG).use {
            it.setInt(1, meldingId.id)
            it.setString(2, vedleggUuid.toString())
            it.setObject(3, now)
            it.setObject(4, now)
            it.setInt(5, number)
            it.setBytes(6, pdf)
            it.executeQuery().toList { getInt("id") }
        }
        if (idList.size != 1) {
            throw SQLException("Creating vedlegg failed, no rows affected.")
        }
        return idList.first()
    }

    companion object {
        private const val QUERY_CREATE_VEDLEGG =
            """
                INSERT INTO vedlegg (
                    id,
                    melding_id,
                    uuid,
                    created_at,
                    updated_at,
                    number,
                    pdf) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?) RETURNING id
            """

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

        private const val QUERY_UPDATE_INNKOMMENDE_PUBLISHED_AT =
            """
                UPDATE MELDING
                SET innkommende_published_at = ?
                WHERE uuid = ?
            """

        private const val QUERY_GET_VEDLEGG =
            """
                SELECT vedlegg.* 
                FROM vedlegg INNER JOIN melding ON (vedlegg.melding_id = melding.id) 
                WHERE melding.uuid = ? 
                AND vedlegg.number=?
            """

        private const val QUERY_GET_UNPUBLISHED_MELDINGER_FRA_BEHANDLER =
            """
                SELECT *
                FROM MELDING
                WHERE innkommende AND innkommende_published_at IS NULL
                ORDER BY created_at ASC
            """
    }
}

private const val QUERY_CREATE_MELDING =
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

private const val QUERY_CREATE_MELDING_FELLESFORMAT =
    """
        INSERT INTO MELDING_FELLESFORMAT (
            id,
            melding_id,
            fellesformat
        ) VALUES (DEFAULT, ?, ?) RETURNING id
    """

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

private fun ResultSet.toPVedlegg() =
    PVedlegg(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        melding_id = getInt("melding_id"),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java),
        number = getInt("number"),
        pdf = getBytes("pdf"),
    )
