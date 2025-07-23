package no.nav.syfo.api.models

import no.nav.syfo.domain.DocumentComponentDTO

data class ReturAvLegeerklaringRequestDTO(
    val document: List<DocumentComponentDTO>,
    val tekst: String,
)
