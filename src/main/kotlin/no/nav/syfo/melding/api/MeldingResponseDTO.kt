package no.nav.syfo.melding.api

import java.time.OffsetDateTime
import java.util.*

data class MeldingResponseDTO(
    val conversations: Map<UUID, List<Melding>>
)

data class Melding(
    val behandlerRef: UUID,
    val tekst: String,
    val tidspunkt: OffsetDateTime,
    val innkommende: Boolean,
)
