package no.nav.syfo.behandlerdialog.domain

import no.nav.syfo.behandlerdialog.api.MeldingTilBehandlerResponseDTO
import no.nav.syfo.dialogmelding.database.domain.PMelding
import no.nav.syfo.dialogmelding.domain.DialogmeldingType
import no.nav.syfo.domain.PersonIdent
import java.time.OffsetDateTime
import java.util.*

data class MeldingTilBehandler(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val type: DialogmeldingType,
    val conversationRef: UUID,
    val parentRef: UUID?,
    val bestiltTidspunkt: OffsetDateTime,
    val arbeidstakerPersonIdent: PersonIdent,
    val behandlerRef: UUID,
    val tekst: String,
    val antallVedlegg: Int,
)

fun MeldingTilBehandler.toMeldingTilBehandlerResponseDTO() = MeldingTilBehandlerResponseDTO(
    behandlerRef = behandlerRef,
    tekst = tekst,
    bestiltTidspunkt = bestiltTidspunkt,
)

fun MeldingTilBehandler.toPMelding() = PMelding(
    uuid = uuid,
    createdAt = createdAt,
    innkommende = false,
    type = type.name,
    conversationRef = conversationRef,
    parentRef = parentRef,
    tidspunkt = bestiltTidspunkt,
    arbeidstakerPersonIdent = arbeidstakerPersonIdent.value,
    behandlerPersonIdent = null,
    behandlerRef = behandlerRef,
    tekst = tekst,
    antallVedlegg = antallVedlegg,
)
