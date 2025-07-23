package no.nav.syfo.api.models

import no.nav.syfo.domain.DocumentComponentDTO

data class PaminnelseRequestDTO(
    val document: List<DocumentComponentDTO>,
)
