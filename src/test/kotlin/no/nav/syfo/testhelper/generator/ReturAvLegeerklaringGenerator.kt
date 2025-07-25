package no.nav.syfo.testhelper.generator

import no.nav.syfo.api.models.ReturAvLegeerklaringRequestDTO
import no.nav.syfo.domain.DocumentComponentDTO
import no.nav.syfo.domain.DocumentComponentType

fun generateReturAvLegeerklaringRequestDTO(): ReturAvLegeerklaringRequestDTO = ReturAvLegeerklaringRequestDTO(
    document = listOf(
        DocumentComponentDTO(
            type = DocumentComponentType.HEADER_H1,
            title = null,
            texts = listOf("Retur av legeerklæring"),
        ),
        DocumentComponentDTO(
            type = DocumentComponentType.PARAGRAPH,
            title = null,
            texts = listOf("Vi viser til tidligere legeerklæring utsendt for din pasient"),
        ),
    ),
    tekst = "Tekst for retur av legeerklæring",
)
