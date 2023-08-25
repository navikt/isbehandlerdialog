package no.nav.syfo.melding.api

import no.nav.syfo.melding.domain.*
import java.util.*

data class MeldingTilBehandlerRequestDTO(
    val type: MeldingType,
    val behandlerIdent: String?,
    val behandlerNavn: String?,
    val behandlerRef: UUID,
    val tekst: String,
    val document: List<DocumentComponentDTO> = emptyList(),
)
