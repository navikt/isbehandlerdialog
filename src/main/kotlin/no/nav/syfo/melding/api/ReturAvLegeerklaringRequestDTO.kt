package no.nav.syfo.melding.api

import no.nav.syfo.melding.domain.*

data class ReturAvLegeerklaringRequestDTO(
    val document: List<DocumentComponentDTO>,
)
