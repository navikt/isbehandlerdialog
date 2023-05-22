package no.nav.syfo.melding.api

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.domain.*
import java.time.OffsetDateTime
import java.util.*

data class MeldingTilBehandlerRequestDTO(
    val behandlerIdent: String?,
    val behandlerNavn: String?,
    val behandlerRef: UUID,
    val tekst: String,
    val document: List<DocumentComponentDTO> = emptyList(),
)

fun MeldingTilBehandlerRequestDTO.toMeldingTilBehandler(personident: PersonIdent): MeldingTilBehandler {
    val now = OffsetDateTime.now()
    return MeldingTilBehandler(
        uuid = UUID.randomUUID(),
        createdAt = now,
        type = MeldingType.FORESPORSEL_PASIENT,
        conversationRef = UUID.randomUUID(),
        parentRef = null,
        tidspunkt = now,
        arbeidstakerPersonIdent = personident,
        behandlerPersonIdent = behandlerIdent?.let { PersonIdent(behandlerIdent) },
        behandlerNavn = behandlerNavn,
        behandlerRef = behandlerRef,
        tekst = tekst,
        document = document,
        antallVedlegg = 0 // TODO: Denne må vel komme fra frontend / regnes ut på en eller annen måte?
    )
}
