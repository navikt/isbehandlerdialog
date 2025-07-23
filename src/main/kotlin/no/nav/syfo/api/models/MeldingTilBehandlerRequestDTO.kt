package no.nav.syfo.api.models

import no.nav.syfo.domain.DocumentComponentDTO
import no.nav.syfo.domain.MeldingType
import java.util.*

data class MeldingTilBehandlerRequestDTO(
    val type: MeldingType,
    val behandlerIdent: String?,
    val behandlerNavn: String?,
    val behandlerRef: UUID,
    val tekst: String,
    val document: List<DocumentComponentDTO> = emptyList(),
)
