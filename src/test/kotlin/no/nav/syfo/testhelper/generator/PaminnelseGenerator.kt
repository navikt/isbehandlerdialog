package no.nav.syfo.testhelper.generator

import no.nav.syfo.melding.api.PaminnelseRequestDTO
import no.nav.syfo.melding.domain.DocumentComponentDTO
import no.nav.syfo.melding.domain.DocumentComponentType

fun generatePaminnelseRequestDTO(): PaminnelseRequestDTO = PaminnelseRequestDTO(
    document = listOf(
        DocumentComponentDTO(
            type = DocumentComponentType.HEADER_H1,
            title = null,
            texts = listOf("Påminnelse"),
        ),
        DocumentComponentDTO(
            type = DocumentComponentType.PARAGRAPH,
            title = null,
            texts = listOf("Vi viser til tidligere forespørsel angående din pasient"),
        ),
    )

)
