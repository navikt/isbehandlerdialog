package no.nav.syfo.melding.api

import no.nav.syfo.melding.domain.*
import java.util.*

data class PaminnelseRequestDTO(
    val document: List<DocumentComponentDTO>,
)
