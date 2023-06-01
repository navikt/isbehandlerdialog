package no.nav.syfo.melding.api

import no.nav.syfo.melding.domain.*
import java.time.OffsetDateTime
import java.util.*

data class PaminnelseRequestDTO(
    val document: List<DocumentComponentDTO>,
)

fun PaminnelseRequestDTO.toMeldingTilBehandler(opprinneligMelding: MeldingTilBehandler): MeldingTilBehandler {
    val now = OffsetDateTime.now()
    return MeldingTilBehandler(
        uuid = UUID.randomUUID(),
        createdAt = now,
        type = MeldingType.FORESPORSEL_PASIENT_PAMINNELSE,
        conversationRef = opprinneligMelding.conversationRef,
        parentRef = opprinneligMelding.uuid,
        tidspunkt = now,
        arbeidstakerPersonIdent = opprinneligMelding.arbeidstakerPersonIdent,
        behandlerPersonIdent = opprinneligMelding.behandlerPersonIdent,
        behandlerNavn = opprinneligMelding.behandlerNavn,
        behandlerRef = opprinneligMelding.behandlerRef,
        tekst = "",
        document = document,
        antallVedlegg = 0, // TODO: Eventuell opprinnelig melding pdf som vedlegg?
        ubesvartPublishedAt = null,
    )
}
