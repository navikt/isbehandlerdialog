package no.nav.syfo.melding.api

import no.nav.syfo.melding.domain.*
import java.time.OffsetDateTime
import java.util.*

data class ReturAvLegeerklaringRequestDTO(
    val document: List<DocumentComponentDTO>,
)

fun ReturAvLegeerklaringRequestDTO.toMeldingTilBehandler(
    opprinneligForesporselLegeerklaring: MeldingTilBehandler,
    innkommendeLegeerklaring: MeldingFraBehandler,
    veilederIdent: String,
): MeldingTilBehandler {
    val now = OffsetDateTime.now()
    return MeldingTilBehandler(
        uuid = UUID.randomUUID(),
        createdAt = now,
        type = MeldingType.HENVENDELSE_RETUR_LEGEERKLARING,
        conversationRef = opprinneligForesporselLegeerklaring.conversationRef,
        parentRef = innkommendeLegeerklaring.uuid,
        tidspunkt = now,
        arbeidstakerPersonIdent = opprinneligForesporselLegeerklaring.arbeidstakerPersonIdent,
        behandlerPersonIdent = opprinneligForesporselLegeerklaring.behandlerPersonIdent,
        behandlerNavn = opprinneligForesporselLegeerklaring.behandlerNavn,
        behandlerRef = opprinneligForesporselLegeerklaring.behandlerRef,
        tekst = "",
        document = document,
        antallVedlegg = 0, // TODO: Eventuell opprinnelig melding pdf som vedlegg?
        ubesvartPublishedAt = null,
        veilederIdent = veilederIdent,
    )
}
