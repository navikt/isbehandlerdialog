package no.nav.syfo.dialogmelding.database.domain

import no.nav.syfo.behandlerdialog.domain.MeldingTilBehandler
import no.nav.syfo.dialogmelding.domain.DialogmeldingType
import no.nav.syfo.domain.PersonIdent
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
