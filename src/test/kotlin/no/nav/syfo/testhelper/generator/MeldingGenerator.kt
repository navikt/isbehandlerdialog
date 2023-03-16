package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.api.MeldingTilBehandlerRequestDTO
import no.nav.syfo.melding.kafka.domain.toMeldingFraBehandler
import java.util.UUID

fun generateMeldingTilBehandlerRequestDTO(
    behandlerRef: UUID = UUID.randomUUID(),
    tekst: String = "Melding til behandler",
) = MeldingTilBehandlerRequestDTO(
    behandlerRef = behandlerRef,
    tekst = tekst,
)

fun generateMeldingFraBehandler(
    conversationRef: UUID,
    personIdent: PersonIdent,
    tekst: String = "Melding fra behandler",
    msgId: UUID = UUID.randomUUID(),
) = generateDialogmeldingFraBehandlerDTO(
    uuid = msgId,
    personIdent = personIdent,
).toMeldingFraBehandler().copy(
    conversationRef = conversationRef,
    tekst = tekst,
)
