package no.nav.syfo.api.models

import no.nav.syfo.domain.DocumentComponentDTO
import no.nav.syfo.domain.Melding
import java.util.*

data class MeldingTilBehandlerRequestDTO(
    val type: Melding.MeldingType,
    val behandlerIdent: String?,
    val behandlerNavn: String?,
    val behandlerRef: UUID,
    val tekst: String,
    val document: List<DocumentComponentDTO> = emptyList(),
)
