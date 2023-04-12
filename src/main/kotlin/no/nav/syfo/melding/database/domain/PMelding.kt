package no.nav.syfo.melding.database.domain

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.domain.*
import java.time.OffsetDateTime
import java.util.UUID

data class PMelding(
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
)

fun PMelding.toMeldingTilBehandler() = MeldingTilBehandler(
    uuid = uuid,
    createdAt = createdAt,
    type = MeldingType.valueOf(type),
    conversationRef = conversationRef,
    parentRef = parentRef,
    bestiltTidspunkt = tidspunkt,
    arbeidstakerPersonIdent = PersonIdent(arbeidstakerPersonIdent),
    behandlerRef = behandlerRef ?: throw IllegalStateException("Mangler behandlerRef for MeldingTilBehandler"),
    tekst = tekst ?: "",
    document = document,
    antallVedlegg = antallVedlegg,
)

fun PMelding.toMeldingFraBehandler() = MeldingFraBehandler(
    uuid = uuid,
    createdAt = createdAt,
    type = MeldingType.valueOf(type),
    conversationRef = conversationRef,
    parentRef = parentRef,
    msgId = msgId ?: "",
    mottattTidspunkt = tidspunkt,
    arbeidstakerPersonIdent = PersonIdent(arbeidstakerPersonIdent),
    behandlerPersonIdent = behandlerPersonIdent?.let { PersonIdent(it) },
    behandlerNavn = behandlerNavn,
    tekst = tekst ?: "",
    antallVedlegg = antallVedlegg,
)
