package no.nav.syfo.melding.database.domain

import no.nav.syfo.melding.domain.MeldingTilBehandler
import no.nav.syfo.melding.domain.DialogmeldingType
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.domain.MeldingFraBehandler
import java.time.OffsetDateTime
import java.util.UUID

data class PMelding(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val innkommende: Boolean,
    val type: String,
    val conversationRef: UUID,
    val parentRef: UUID?,
    val tidspunkt: OffsetDateTime,
    val arbeidstakerPersonIdent: String,
    val behandlerPersonIdent: String?,
    val behandlerRef: UUID?,
    val tekst: String?,
    val antallVedlegg: Int,
)

fun PMelding.toMeldingTilBehandler() = MeldingTilBehandler(
    uuid = uuid,
    createdAt = createdAt,
    type = DialogmeldingType.valueOf(type),
    conversationRef = conversationRef,
    parentRef = parentRef,
    bestiltTidspunkt = tidspunkt,
    arbeidstakerPersonIdent = PersonIdent(arbeidstakerPersonIdent),
    behandlerRef = behandlerRef ?: throw IllegalStateException("Mangler behandlerRef for MeldingTilBehandler"),
    tekst = tekst ?: "",
    antallVedlegg = antallVedlegg,
)

fun PMelding.toMeldingFraBehandler() = MeldingFraBehandler(
    uuid = uuid,
    createdAt = createdAt,
    type = DialogmeldingType.valueOf(type),
    conversationRef = conversationRef,
    parentRef = parentRef,
    mottattTidspunkt = tidspunkt,
    arbeidstakerPersonIdent = PersonIdent(arbeidstakerPersonIdent),
    behandlerPersonIdent = behandlerPersonIdent?.let { PersonIdent(it) },
    tekst = tekst ?: "",
    antallVedlegg = antallVedlegg,
)
