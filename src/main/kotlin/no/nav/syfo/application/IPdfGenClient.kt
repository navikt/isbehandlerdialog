package no.nav.syfo.application

import no.nav.syfo.domain.DocumentComponentDTO
import no.nav.syfo.domain.MeldingType
import no.nav.syfo.infrastructure.kafka.legeerklaring.LegeerklaringDTO

interface IPdfGenClient {
    suspend fun generateDialogPdf(
        callId: String,
        mottakerNavn: String,
        documentComponentDTOList: List<DocumentComponentDTO>,
        meldingType: MeldingType,
    ): ByteArray?

    suspend fun generateLegeerklaring(legeerklaringDTO: LegeerklaringDTO): ByteArray?
}
