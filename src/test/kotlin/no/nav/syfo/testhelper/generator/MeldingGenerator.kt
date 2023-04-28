package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.melding.api.MeldingTilBehandlerRequestDTO
import no.nav.syfo.melding.domain.DocumentComponentDTO
import no.nav.syfo.melding.domain.DocumentComponentType
import no.nav.syfo.melding.kafka.domain.toMeldingFraBehandler
import no.nav.syfo.testhelper.UserConstants
import java.util.UUID

fun generateMeldingTilBehandlerRequestDTO(
    behandlerRef: UUID = UUID.randomUUID(),
    tekst: String = "Melding til behandler",
) = MeldingTilBehandlerRequestDTO(
    behandlerRef = behandlerRef,
    behandlerIdent = UserConstants.BEHANDLER_PERSONIDENT.value,
    behandlerNavn = UserConstants.BEHANDLER_NAVN,
    tekst = tekst,
    document = listOf(
        DocumentComponentDTO(
            type = DocumentComponentType.HEADER_H1,
            title = null,
            texts = listOf("Dialogmelding"),
        ),
        DocumentComponentDTO(
            type = DocumentComponentType.PARAGRAPH,
            title = null,
            texts = listOf(tekst),
        ),
        DocumentComponentDTO(
            type = DocumentComponentType.PARAGRAPH,
            key = "Standardtekst",
            title = null,
            texts = listOf("Dette er en standardtekst"),
        ),
    )
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
