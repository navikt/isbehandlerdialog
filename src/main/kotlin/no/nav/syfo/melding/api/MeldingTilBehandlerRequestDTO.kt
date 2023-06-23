package no.nav.syfo.melding.api

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.domain.*
import java.time.OffsetDateTime
import java.util.*

data class MeldingTilBehandlerRequestDTO(
    val type: MeldingType,
    val behandlerIdent: String?,
    val behandlerNavn: String?,
    val behandlerRef: UUID,
    val tekst: String,
    val document: List<DocumentComponentDTO> = emptyList(),
)

fun MeldingTilBehandlerRequestDTO.toMeldingTilBehandler(personIdent: PersonIdent, veilederIdent: String): MeldingTilBehandler {
    val now = OffsetDateTime.now()
    return MeldingTilBehandler(
        uuid = UUID.randomUUID(),
        createdAt = now,
        type = MeldingType.FORESPORSEL_PASIENT,
        conversationRef = UUID.randomUUID(),
        parentRef = null,
        tidspunkt = now,
        arbeidstakerPersonIdent = personIdent,
        behandlerPersonIdent = behandlerIdent?.let { PersonIdent(behandlerIdent) },
        behandlerNavn = behandlerNavn,
        behandlerRef = behandlerRef,
        tekst = tekst,
        document = document,
        antallVedlegg = 0,
        ubesvartPublishedAt = null,
        veilederIdent = veilederIdent,
    )
}
