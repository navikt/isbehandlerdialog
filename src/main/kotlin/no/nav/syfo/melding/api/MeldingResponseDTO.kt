package no.nav.syfo.melding.api

import no.nav.syfo.melding.domain.DocumentComponentDTO
import java.time.OffsetDateTime
import java.util.*

data class MeldingResponseDTO(
    val conversations: Map<UUID, List<Melding>>
)

data class Melding(
    val behandlerRef: UUID,
    val behandlerNavn: String?,
    val tekst: String,
    val document: List<DocumentComponentDTO>,
    val tidspunkt: OffsetDateTime,
    val innkommende: Boolean,
)
