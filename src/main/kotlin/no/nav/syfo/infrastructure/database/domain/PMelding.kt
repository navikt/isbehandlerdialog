package no.nav.syfo.infrastructure.database.domain

import no.nav.syfo.domain.DocumentComponentDTO
import no.nav.syfo.domain.MeldingFraBehandler
import no.nav.syfo.domain.MeldingTilBehandler
import no.nav.syfo.domain.MeldingType
import no.nav.syfo.domain.PersonIdent
import java.time.OffsetDateTime
import java.util.UUID

data class PMelding(
    val id: Id,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val innkommende: Boolean,
    val type: String,
    val conversationRef: UUID,
    val parentRef: UUID?,
    val msgId: String?,
    val tidspunkt: OffsetDateTime,
    val arbeidstakerPersonIdent: String,
    val behandlerPersonIdent: String?,
    val behandlerNavn: String?,
    val behandlerRef: UUID?,
    val tekst: String?,
    val document: List<DocumentComponentDTO>,
    val antallVedlegg: Int,
    val innkommendePublishedAt: OffsetDateTime?,
    val journalpostId: String?,
    val ubesvartPublishedAt: OffsetDateTime?,
    val veilederIdent: String?,
    val avvistPublishedAt: OffsetDateTime?,
) {
    @JvmInline
    value class Id(val id: Int)
}

fun PMelding.toMeldingTilBehandler() = MeldingTilBehandler(
    uuid = uuid,
    createdAt = createdAt,
    type = MeldingType.valueOf(type),
    conversationRef = conversationRef,
    parentRef = parentRef,
    tidspunkt = tidspunkt,
    arbeidstakerPersonIdent = PersonIdent(arbeidstakerPersonIdent),
    behandlerPersonIdent = behandlerPersonIdent?.let { PersonIdent(it) },
    behandlerNavn = behandlerNavn,
    behandlerRef = behandlerRef ?: throw IllegalStateException("Mangler behandlerRef for MeldingTilBehandler"),
    tekst = tekst ?: "",
    document = document,
    antallVedlegg = antallVedlegg,
    ubesvartPublishedAt = ubesvartPublishedAt,
    veilederIdent = veilederIdent,
)

fun PMelding.toMeldingFraBehandler() = MeldingFraBehandler(
    uuid = uuid,
    createdAt = createdAt,
    type = MeldingType.valueOf(type),
    conversationRef = conversationRef,
    parentRef = parentRef,
    msgId = msgId ?: "",
    tidspunkt = tidspunkt,
    arbeidstakerPersonIdent = PersonIdent(arbeidstakerPersonIdent),
    behandlerPersonIdent = behandlerPersonIdent?.let { PersonIdent(it) },
    behandlerNavn = behandlerNavn,
    tekst = tekst ?: "",
    antallVedlegg = antallVedlegg,
    innkommendePublishedAt = innkommendePublishedAt,
)
