package no.nav.syfo.melding.api

import no.nav.syfo.melding.domain.MeldingTilBehandler
import no.nav.syfo.melding.domain.DialogmeldingType
import no.nav.syfo.domain.PersonIdent
import java.time.OffsetDateTime
import java.util.*

data class MeldingTilBehandlerRequestDTO(
    val behandlerRef: UUID,
    val tekst: String,
)

fun MeldingTilBehandlerRequestDTO.toMeldingTilBehandler(personident: PersonIdent): MeldingTilBehandler {
    val now = OffsetDateTime.now()
    return MeldingTilBehandler(
        uuid = UUID.randomUUID(),
        createdAt = now,
        type = DialogmeldingType.DIALOG_FORESPORSEL,
        conversationRef = UUID.randomUUID(),
        parentRef = null,
        bestiltTidspunkt = now,
        arbeidstakerPersonIdent = personident,
        behandlerRef = behandlerRef,
        tekst = tekst,
        antallVedlegg = 0 // TODO: Denne må vel komme fra frontend / regnes ut på en eller annen måte?
    )
}
