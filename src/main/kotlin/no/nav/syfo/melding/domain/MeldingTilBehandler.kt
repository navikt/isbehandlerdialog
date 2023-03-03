package no.nav.syfo.melding.domain

import no.nav.syfo.melding.database.domain.PMelding
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.api.Melding
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

fun MeldingTilBehandler.toMelding() = Melding(
    behandlerRef = behandlerRef,
    tekst = tekst,
    tidspunkt = bestiltTidspunkt,
    innkommende = false,
)
